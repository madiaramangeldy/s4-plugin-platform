package kz.madiyar.plugins.host.lifecycle;

import kz.madiyar.plugins.host.PluginPlatformException;

/** Thrown when an action is not allowed in the current lifecycle state (R3). */
public class IllegalTransitionException extends PluginPlatformException {

    private final PluginState from;
    private final String action;

    public IllegalTransitionException(PluginState from, String action) {
        super("Illegal transition: cannot '" + action + "' a plugin in state " + from);
        this.from = from;
        this.action = action;
    }

    public PluginState from() {
        return from;
    }

    public String action() {
        return action;
    }
}
