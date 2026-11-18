package kz.madiyar.plugins.host.runtime;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;
import kz.madiyar.plugins.host.PluginDescriptor;
import kz.madiyar.plugins.host.PluginLogSink;

/** Concrete factory for API v2: the plugin already implements {@link Plugin}, no wrapping needed. */
public final class V2RuntimeFactory implements PluginRuntimeFactory {

    @Override
    public int apiVersion() {
        return 2;
    }

    @Override
    public Plugin createPlugin(Class<?> mainClass) {
        return Instantiator.newInstance(mainClass, Plugin.class, 2);
    }

    @Override
    public PluginContext createContext(PluginDescriptor descriptor, PluginLogSink sink) {
        return new HostPluginContext(descriptor.id(), sink, "");
    }
}
