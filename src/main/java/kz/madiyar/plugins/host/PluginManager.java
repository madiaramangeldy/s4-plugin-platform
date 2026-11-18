package kz.madiyar.plugins.host;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.host.barrier.FaultBarrier;
import kz.madiyar.plugins.host.barrier.FaultBarrierPlugin;
import kz.madiyar.plugins.host.lifecycle.PluginState;
import kz.madiyar.plugins.host.loading.DescriptorReader;
import kz.madiyar.plugins.host.loading.ForbiddenApiScanner;
import kz.madiyar.plugins.host.loading.PluginClassLoader;
import kz.madiyar.plugins.host.resolve.DependencyResolver;
import kz.madiyar.plugins.host.runtime.PluginRuntimeFactory;
import kz.madiyar.plugins.host.runtime.V1RuntimeFactory;
import kz.madiyar.plugins.host.runtime.V2RuntimeFactory;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facade: the single entry point of the platform. The application only talks to this class;
 * descriptor reading, dependency resolution, class loading, scanning, factories, fault barrier
 * and lifecycle are hidden behind it.
 *
 * <p>DI: every collaborator is passed into the constructor (constructor injection), so tests can
 * replace any of them. {@link #createDefault} is the composition root that wires the real ones.
 */
public final class PluginManager implements AutoCloseable {

    private final DescriptorReader descriptorReader;
    private final DependencyResolver resolver;
    private final ForbiddenApiScanner scanner;
    private final Map<Integer, PluginRuntimeFactory> factories = new HashMap<>();
    private final Duration callTimeout;
    private final PluginLogSink logSink;
    private final ClassLoader apiLoader = Plugin.class.getClassLoader();

    /** Installed plugins in load order (dependencies first). */
    private final Map<String, LoadedPlugin> plugins = new LinkedHashMap<>();

    public PluginManager(DescriptorReader descriptorReader, DependencyResolver resolver, ForbiddenApiScanner scanner,
                         Collection<PluginRuntimeFactory> runtimeFactories, Duration callTimeout, PluginLogSink logSink) {
        this.descriptorReader = descriptorReader;
        this.resolver = resolver;
        this.scanner = scanner;
        this.callTimeout = callTimeout;
        this.logSink = logSink;
        for (PluginRuntimeFactory factory : runtimeFactories) {
            factories.put(factory.apiVersion(), factory);
        }
    }

    /** Composition root: the real implementations wired together. */
    public static PluginManager createDefault(Duration callTimeout, PluginLogSink logSink) {
        return new PluginManager(new DescriptorReader(), new DependencyResolver(), new ForbiddenApiScanner(),
                List.of(new V1RuntimeFactory(), new V2RuntimeFactory()), callTimeout, logSink);
    }

    // ------------------------------------------------------------------ install / start / stop

    /**
     * Installs plugin JARs: INSTALLED → RESOLVED. All or nothing: if any JAR is invalid,
     * has a missing dependency, a cycle or a forbidden call, nothing is installed.
     *
     * @return ids of the new plugins in load order
     */
    public synchronized List<String> install(Collection<Path> jars) {
        Map<String, Path> newJars = new LinkedHashMap<>();
        List<PluginDescriptor> all = new ArrayList<>();
        plugins.values().forEach(p -> all.add(p.descriptor()));
        for (Path jar : jars) {
            PluginDescriptor descriptor = descriptorReader.read(jar);
            if (plugins.containsKey(descriptor.id()) || newJars.containsKey(descriptor.id())) {
                throw new PluginPlatformException("Plugin '" + descriptor.id() + "' is already installed");
            }
            newJars.put(descriptor.id(), jar);
            all.add(descriptor);
        }

        List<PluginDescriptor> order = resolver.loadOrder(all);
        newJars.forEach((id, jar) -> scanner.check(jar, id));

        List<LoadedPlugin> loaded = new ArrayList<>();
        try {
            for (PluginDescriptor descriptor : order) {
                Path jar = newJars.get(descriptor.id());
                if (jar != null) {
                    loaded.add(load(descriptor, jar));
                }
            }
        } catch (RuntimeException e) {
            loaded.forEach(LoadedPlugin::close);
            throw e;
        }

        List<String> ids = new ArrayList<>();
        for (LoadedPlugin plugin : loaded) {
            plugin.lifecycle().resolve();
            plugins.put(plugin.id(), plugin);
            ids.add(plugin.id());
        }
        return ids;
    }

    /** RESOLVED/STOPPED → ACTIVE. All dependencies must already be ACTIVE. */
    public synchronized void start(String id) {
        LoadedPlugin plugin = get(id);
        plugin.state().start();   // throws IllegalTransitionException before any plugin code runs
        for (String dependency : plugin.descriptor().dependencies()) {
            if (get(dependency).state() != PluginState.ACTIVE) {
                throw new PluginPlatformException(
                        "Cannot start '" + id + "': dependency '" + dependency + "' is not active");
            }
        }
        plugin.plugin().start(plugin.context());   // on failure the state stays as it was
        plugin.lifecycle().start();
    }

    /**
     * Starts every installed plugin that is not active, in load order. A plugin that fails is
     * reported and skipped; the plugins that depend on it are then skipped too.
     *
     * @return ids of plugins that are ACTIVE after the call
     */
    public synchronized List<String> startAll() {
        for (LoadedPlugin plugin : plugins.values()) {
            if (plugin.state() == PluginState.ACTIVE) {
                continue;
            }
            try {
                start(plugin.id());
            } catch (PluginPlatformException e) {
                logSink.log(plugin.id(), "not started: " + e.getMessage());
            }
        }
        return activePlugins();
    }

    /** ACTIVE → STOPPED. Active plugins that depend on this one are stopped first. */
    public synchronized void stop(String id) {
        LoadedPlugin plugin = get(id);
        plugin.state().stop();   // throws IllegalTransitionException if not ACTIVE
        for (LoadedPlugin other : reversed()) {
            if (other.descriptor().dependencies().contains(id) && other.state() == PluginState.ACTIVE) {
                stop(other.id());
            }
        }
        stopQuietly(plugin);
    }

    /** Removes a plugin completely. Refused if another installed plugin depends on it. */
    public synchronized void uninstall(String id) {
        LoadedPlugin plugin = get(id);
        for (LoadedPlugin other : plugins.values()) {
            if (other.descriptor().dependencies().contains(id)) {
                throw new PluginPlatformException(
                        "Cannot uninstall '" + id + "': plugin '" + other.id() + "' depends on it");
            }
        }
        if (plugin.state() == PluginState.ACTIVE) {
            stopQuietly(plugin);
        }
        plugins.remove(id);
        plugin.close();
    }

    // ------------------------------------------------------------------ calls

    /** Sends a request to an ACTIVE plugin. A plugin that times out is moved to STOPPED. */
    public String call(String id, String request) {
        LoadedPlugin plugin;
        synchronized (this) {
            plugin = get(id);
            if (plugin.state() != PluginState.ACTIVE) {
                throw new PluginPlatformException("Plugin '" + id + "' is not active (state " + plugin.state() + ")");
            }
        }
        try {
            return plugin.plugin().handle(request);
        } catch (PluginTimeoutException e) {
            synchronized (this) {
                if (plugin.state() == PluginState.ACTIVE) {
                    plugin.lifecycle().stop();
                }
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------ hot reload (R6)

    /**
     * Replaces a plugin with a new JAR without restarting the host.
     * The new version is loaded and started first; only if that succeeds is the old one
     * stopped and released. If anything fails, the old version keeps running.
     */
    public synchronized void reload(String id, Path newJar) {
        LoadedPlugin old = get(id);
        PluginDescriptor descriptor = descriptorReader.read(newJar);
        if (!descriptor.id().equals(id)) {
            throw new PluginPlatformException(
                    "Reload of '" + id + "' rejected: the new JAR is plugin '" + descriptor.id() + "'");
        }
        List<PluginDescriptor> all = new ArrayList<>();
        plugins.values().forEach(p -> all.add(p.id().equals(id) ? descriptor : p.descriptor()));
        resolver.loadOrder(all);
        scanner.check(newJar, id);

        LoadedPlugin fresh = load(descriptor, newJar);
        fresh.lifecycle().resolve();
        boolean wasActive = old.state() == PluginState.ACTIVE;
        if (wasActive) {
            try {
                fresh.plugin().start(fresh.context());
            } catch (RuntimeException e) {
                fresh.close();
                throw new PluginPlatformException("Reload of '" + id + "' failed; old version keeps running", e);
            }
            stopQuietly(old);
            fresh.lifecycle().start();
        }
        plugins.put(id, fresh);   // same key: keeps its place in the load order
        old.close();
        logSink.log(id, "reloaded: " + old.descriptor().version() + " -> " + descriptor.version());
    }

    // ------------------------------------------------------------------ queries

    public synchronized PluginState state(String id) {
        return get(id).state();
    }

    public synchronized String version(String id) {
        return get(id).descriptor().version();
    }

    /** Installed plugin ids in load order. */
    public synchronized List<String> installedPlugins() {
        return List.copyOf(plugins.keySet());
    }

    public synchronized List<String> activePlugins() {
        return plugins.values().stream()
                .filter(p -> p.state() == PluginState.ACTIVE)
                .map(LoadedPlugin::id)
                .toList();
    }

    /** For tests only: lets a test hold a WeakReference to the loader and prove it is collected. */
    synchronized ClassLoader classLoaderOf(String id) {
        return get(id).classLoader();
    }

    /** Stops all plugins in reverse load order and releases them. */
    @Override
    public synchronized void close() {
        for (LoadedPlugin plugin : reversed()) {
            if (plugin.state() == PluginState.ACTIVE) {
                stopQuietly(plugin);
            }
            plugin.close();
        }
        plugins.clear();
    }

    // ------------------------------------------------------------------ internals

    private LoadedPlugin get(String id) {
        LoadedPlugin plugin = plugins.get(id);
        if (plugin == null) {
            throw new PluginPlatformException("Unknown plugin '" + id + "'");
        }
        return plugin;
    }

    private List<LoadedPlugin> reversed() {
        List<LoadedPlugin> list = new ArrayList<>(plugins.values());
        java.util.Collections.reverse(list);
        return list;
    }

    /** Calls plugin.stop(); a failing stop() is logged, the plugin is considered stopped anyway. */
    private void stopQuietly(LoadedPlugin plugin) {
        try {
            plugin.plugin().stop();
        } catch (PluginFailureException e) {
            logSink.log(plugin.id(), "stop failed: " + e.getMessage());
        }
        plugin.lifecycle().stop();
    }

    /** Creates loader → barrier → plugin object (inside the barrier) → proxy. Plugin code never runs on our thread. */
    private LoadedPlugin load(PluginDescriptor descriptor, Path jar) {
        PluginRuntimeFactory factory = factories.get(descriptor.apiVersion());
        if (factory == null) {
            throw new PluginPlatformException(
                    "Plugin '" + descriptor.id() + "': no runtime for api version " + descriptor.apiVersion());
        }
        PluginClassLoader loader;
        try {
            loader = new PluginClassLoader(descriptor.id(), jar.toUri().toURL(), apiLoader);
        } catch (MalformedURLException e) {
            throw new PluginPlatformException("Bad JAR path: " + jar, e);
        }
        FaultBarrier barrier = new FaultBarrier(descriptor.id(), callTimeout, loader);
        try {
            Plugin raw = barrier.call("create",
                    () -> factory.createPlugin(loader.loadClass(descriptor.mainClass())));
            Plugin protectedPlugin = new FaultBarrierPlugin(raw, barrier);
            return new LoadedPlugin(descriptor, loader, barrier, protectedPlugin,
                    factory.createContext(descriptor, logSink));
        } catch (RuntimeException e) {
            barrier.close();
            try {
                loader.close();
            } catch (java.io.IOException ignored) {
                // nothing to recover
            }
            throw e;
        }
    }
}
