package kz.madiyar.plugins.host.loading;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * R2: one ClassLoader per plugin.
 *
 * <p>What a plugin can see:
 * <ul>
 *   <li>JDK classes ({@code java.*} etc.) — via the platform class loader (parent);</li>
 *   <li>the plugin API ({@code kz.madiyar.plugins.api.*}) — delegated to the host loader,
 *       so host and plugin share the SAME {@code Plugin} interface;</li>
 *   <li>its own classes — from its own JAR.</li>
 * </ul>
 * Everything else on the host classpath (in particular {@code kz.madiyar.plugins.host.*})
 * is invisible: the parent is the platform loader, not the application loader.
 *
 * <p>Because every plugin has its own loader, dropping the loader (and all objects
 * created from it) lets the GC unload the plugin's classes — needed for hot reload (R6).
 */
public final class PluginClassLoader extends URLClassLoader {

    static final String API_PACKAGE = "kz.madiyar.plugins.api.";
    static final String HOST_PACKAGE = "kz.madiyar.plugins.host.";

    private final ClassLoader hostLoader;

    public PluginClassLoader(String pluginId, URL jar, ClassLoader hostLoader) {
        super("plugin:" + pluginId, new URL[]{jar}, ClassLoader.getPlatformClassLoader());
        this.hostLoader = hostLoader;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith(API_PACKAGE)) {
            return hostLoader.loadClass(name);
        }
        if (name.startsWith(HOST_PACKAGE)) {
            throw new ClassNotFoundException(
                    name + " is internal to the host; plugins may use only " + API_PACKAGE + "*");
        }
        return super.loadClass(name, resolve);
    }
}
