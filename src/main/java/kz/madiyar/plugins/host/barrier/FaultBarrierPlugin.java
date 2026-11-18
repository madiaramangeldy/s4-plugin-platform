package kz.madiyar.plugins.host.barrier;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;

/**
 * R5 — Proxy pattern (protection proxy). Has the same interface as the real plugin,
 * so the host cannot tell the difference, but every call goes through the {@link FaultBarrier}.
 * The host never holds a reference to the unprotected plugin object.
 */
public final class FaultBarrierPlugin implements Plugin {

    private final Plugin target;
    private final FaultBarrier barrier;

    public FaultBarrierPlugin(Plugin target, FaultBarrier barrier) {
        this.target = target;
        this.barrier = barrier;
    }

    @Override
    public void start(PluginContext context) {
        barrier.call("start", () -> {
            target.start(context);
            return null;
        });
    }

    @Override
    public String handle(String request) {
        return barrier.call("handle", () -> target.handle(request));
    }

    /** A quarantined plugin is not called again: its thread may still be stuck. */
    @Override
    public void stop() {
        if (barrier.isQuarantined()) {
            return;
        }
        barrier.call("stop", () -> {
            target.stop();
            return null;
        });
    }
}
