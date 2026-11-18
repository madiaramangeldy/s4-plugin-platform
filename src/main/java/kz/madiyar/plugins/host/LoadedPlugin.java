package kz.madiyar.plugins.host;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;
import kz.madiyar.plugins.host.barrier.FaultBarrier;
import kz.madiyar.plugins.host.lifecycle.PluginLifecycle;
import kz.madiyar.plugins.host.lifecycle.PluginState;
import kz.madiyar.plugins.host.loading.PluginClassLoader;

import java.io.IOException;

/** Everything the host keeps about one installed plugin. Internal to the host. */
final class LoadedPlugin implements AutoCloseable {

    private final PluginDescriptor descriptor;
    private final PluginClassLoader loader;
    private final FaultBarrier barrier;
    private final Plugin plugin;            // always the protected proxy, never the raw plugin
    private final PluginContext context;
    private final PluginLifecycle lifecycle = new PluginLifecycle();

    LoadedPlugin(PluginDescriptor descriptor, PluginClassLoader loader, FaultBarrier barrier,
                 Plugin plugin, PluginContext context) {
        this.descriptor = descriptor;
        this.loader = loader;
        this.barrier = barrier;
        this.plugin = plugin;
        this.context = context;
    }

    PluginDescriptor descriptor() {
        return descriptor;
    }

    String id() {
        return descriptor.id();
    }

    Plugin plugin() {
        return plugin;
    }

    PluginContext context() {
        return context;
    }

    PluginLifecycle lifecycle() {
        return lifecycle;
    }

    PluginState state() {
        return lifecycle.state();
    }

    ClassLoader classLoader() {
        return loader;
    }

    /** Stops the plugin thread and closes the JAR, so the loader can be garbage collected. */
    @Override
    public void close() {
        barrier.close();
        try {
            loader.close();
        } catch (IOException ignored) {
            // the JAR is only read; nothing to recover
        }
    }
}
