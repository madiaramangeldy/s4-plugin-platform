package kz.madiyar.plugins.host;

/** A plugin misbehaved (threw, crashed, timed out). The host itself is fine. */
public class PluginFailureException extends PluginPlatformException {

    private final String pluginId;

    public PluginFailureException(String pluginId, String message) {
        super("Plugin '" + pluginId + "': " + message);
        this.pluginId = pluginId;
    }

    public PluginFailureException(String pluginId, String message, Throwable cause) {
        super("Plugin '" + pluginId + "': " + message, cause);
        this.pluginId = pluginId;
    }

    public String pluginId() {
        return pluginId;
    }
}
