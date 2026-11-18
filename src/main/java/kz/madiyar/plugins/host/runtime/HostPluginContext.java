package kz.madiyar.plugins.host.runtime;

import kz.madiyar.plugins.api.PluginContext;
import kz.madiyar.plugins.host.PluginLogSink;

/** The host-side implementation of {@link PluginContext} that is injected into plugins (DI). */
final class HostPluginContext implements PluginContext {

    private final String pluginId;
    private final PluginLogSink sink;
    private final String prefix;

    HostPluginContext(String pluginId, PluginLogSink sink, String prefix) {
        this.pluginId = pluginId;
        this.sink = sink;
        this.prefix = prefix;
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    @Override
    public void log(String message) {
        sink.log(pluginId, prefix + message);
    }
}
