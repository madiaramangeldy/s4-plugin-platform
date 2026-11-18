package kz.madiyar.plugins.host.lifecycle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginStateTest {

    private static UnaryOperator<PluginState> action(String name) {
        return switch (name) {
            case "resolve" -> PluginState::resolve;
            case "start" -> PluginState::start;
            case "stop" -> PluginState::stop;
            default -> throw new IllegalArgumentException(name);
        };
    }

    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @CsvSource({
            "INSTALLED, resolve, RESOLVED",
            "RESOLVED,  start,   ACTIVE",
            "ACTIVE,    stop,    STOPPED",
            "STOPPED,   start,   ACTIVE"
    })
    void legalTransitions(PluginState from, String actionName, PluginState expected) {
        assertSame(expected, action(actionName).apply(from));
    }

    @ParameterizedTest(name = "{0} --{1}--> rejected")
    @CsvSource({
            "INSTALLED, start",
            "INSTALLED, stop",
            "RESOLVED,  resolve",
            "RESOLVED,  stop",
            "ACTIVE,    resolve",
            "ACTIVE,    start",
            "STOPPED,   resolve",
            "STOPPED,   stop"
    })
    void illegalTransitionsAreRejected(PluginState from, String actionName) {
        IllegalTransitionException e = assertThrows(IllegalTransitionException.class,
                () -> action(actionName).apply(from));
        assertSame(from, e.from());
        assertEquals(actionName, e.action());
        assertTrue(e.getMessage().contains(from.name()));
        assertTrue(e.getMessage().contains(actionName));
    }

    @Test
    void lifecycleStartsInstalledAndWalksFullCycle() {
        PluginLifecycle lifecycle = new PluginLifecycle();
        assertSame(PluginState.INSTALLED, lifecycle.state());

        lifecycle.resolve();
        assertSame(PluginState.RESOLVED, lifecycle.state());
        lifecycle.start();
        assertSame(PluginState.ACTIVE, lifecycle.state());
        lifecycle.stop();
        assertSame(PluginState.STOPPED, lifecycle.state());
        lifecycle.start();
        assertSame(PluginState.ACTIVE, lifecycle.state());
    }

    @Test
    void failedTransitionKeepsCurrentState() {
        PluginLifecycle lifecycle = new PluginLifecycle();
        assertThrows(IllegalTransitionException.class, lifecycle::start);
        assertSame(PluginState.INSTALLED, lifecycle.state());
        assertThrows(IllegalTransitionException.class, lifecycle::stop);
        assertSame(PluginState.INSTALLED, lifecycle.state());
    }
}
