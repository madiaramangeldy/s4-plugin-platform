package kz.madiyar.plugins.host.runtime;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;
import kz.madiyar.plugins.host.PluginDescriptor;
import kz.madiyar.plugins.host.PluginLogSink;

/**
 * Abstract Factory: creates the family of objects that a plugin of one API version needs —
 * the plugin object (seen by the host as {@link Plugin}) and the context injected into it.
 * One concrete factory per API version; the manager picks it by {@code apiVersion}.
 * Supporting API v3 means adding one new factory class, not editing the manager.
 */
public interface PluginRuntimeFactory {

    int apiVersion();

    Plugin createPlugin(Class<?> mainClass);

    PluginContext createContext(PluginDescriptor descriptor, PluginLogSink sink);
}
