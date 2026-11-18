package kz.madiyar.plugins.api;

/**
 * Plugin API v2 — the only contract a modern plugin implements.
 * Plugins see this package and nothing from {@code kz.madiyar.plugins.host}.
 */
public interface Plugin {

    /** Called once when the plugin becomes ACTIVE. Host services are injected via the context. */
    void start(PluginContext context);

    /** Handles one request from the host. */
    String handle(String request);

    /** Called when the plugin is stopped or unloaded. Must release resources. */
    void stop();
}
