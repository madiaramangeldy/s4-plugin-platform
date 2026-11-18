package kz.madiyar.plugins.host.lifecycle;

/**
 * R3 — State pattern. Each state decides which actions are legal from it
 * and which state comes next. Any action not overridden is illegal.
 *
 * <pre>
 * INSTALLED --resolve--> RESOLVED --start--> ACTIVE --stop--> STOPPED
 *                                              ^                 |
 *                                              +------start------+
 * </pre>
 */
public enum PluginState {

    INSTALLED {
        @Override
        public PluginState resolve() {
            return RESOLVED;
        }
    },

    RESOLVED {
        @Override
        public PluginState start() {
            return ACTIVE;
        }
    },

    ACTIVE {
        @Override
        public PluginState stop() {
            return STOPPED;
        }
    },

    STOPPED {
        @Override
        public PluginState start() {
            return ACTIVE;
        }
    };

    public PluginState resolve() {
        throw illegal("resolve");
    }

    public PluginState start() {
        throw illegal("start");
    }

    public PluginState stop() {
        throw illegal("stop");
    }

    private IllegalTransitionException illegal(String action) {
        return new IllegalTransitionException(this, action);
    }
}
