package kz.madiyar.plugins.host.runtime;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;
import kz.madiyar.plugins.api.v1.LegacyPlugin;
import kz.madiyar.plugins.host.PluginDescriptor;
import kz.madiyar.plugins.host.PluginLogSink;

/** Concrete factory for API v1: wraps the old plugin in {@link LegacyPluginAdapter} (R4). */
public final class V1RuntimeFactory implements PluginRuntimeFactory {

    @Override
    public int apiVersion() {
        return 1;
    }

    @Override
    public Plugin createPlugin(Class<?> mainClass) {
        return new LegacyPluginAdapter(Instantiator.newInstance(mainClass, LegacyPlugin.class, 1));
    }

    /** v1 plugins never log themselves; the adapter logs for them, marked with [api-v1]. */
    @Override
    public PluginContext createContext(PluginDescriptor descriptor, PluginLogSink sink) {
        return new HostPluginContext(descriptor.id(), sink, "[api-v1] ");
    }
}
