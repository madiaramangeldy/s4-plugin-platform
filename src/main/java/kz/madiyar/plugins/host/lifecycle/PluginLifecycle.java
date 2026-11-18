package kz.madiyar.plugins.host.lifecycle;

/**
 * Context of the State pattern: holds the current state of one plugin and
 * delegates every action to it. Synchronized so two threads cannot
 * move the same plugin at the same time.
 */
public final class PluginLifecycle {

    private PluginState state = PluginState.INSTALLED;

    public synchronized PluginState state() {
        return state;
    }

    public synchronized void resolve() {
        state = state.resolve();
    }

    public synchronized void start() {
        state = state.start();
    }

    public synchronized void stop() {
        state = state.stop();
    }
}
