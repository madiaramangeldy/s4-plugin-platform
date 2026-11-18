package kz.madiyar.plugins.host.runtime;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;
import kz.madiyar.plugins.api.v1.LegacyPlugin;

/**
 * R4 — Adapter pattern. Makes an old v1 plugin look like a v2 {@link Plugin}:
 * <pre>
 *   start(context) → init()
 *   handle(request) → process(request)
 *   stop()          → destroy()
 * </pre>
 * The v1 plugin is not changed and not even recompiled.
 */
public final class LegacyPluginAdapter implements Plugin {

    private final LegacyPlugin legacy;
    private PluginContext context;

    public LegacyPluginAdapter(LegacyPlugin legacy) {
        this.legacy = legacy;
    }

    @Override
    public void start(PluginContext context) {
        this.context = context;
        legacy.init();
        context.log("started through adapter");
    }

    @Override
    public String handle(String request) {
        return legacy.process(request);
    }

    @Override
    public void stop() {
        legacy.destroy();
        if (context != null) {
            context.log("stopped through adapter");
        }
    }
}
