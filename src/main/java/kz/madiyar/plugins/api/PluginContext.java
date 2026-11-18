package kz.madiyar.plugins.api;

/**
 * Services the host gives to a plugin (dependency injection: the plugin
 * never creates or looks up host objects itself).
 */
public interface PluginContext {

    String pluginId();

    void log(String message);
}
