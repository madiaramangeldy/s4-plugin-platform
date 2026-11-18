package kz.madiyar.plugins.host;

/** Where messages that plugins write via {@code PluginContext.log} end up. */
@FunctionalInterface
public interface PluginLogSink {

    void log(String pluginId, String message);

    /** Default sink: prints to standard output. */
    static PluginLogSink console() {
        return (pluginId, message) -> System.out.println("[plugin:" + pluginId + "] " + message);
    }
}
