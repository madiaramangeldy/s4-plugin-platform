package kz.madiyar.plugins.host.barrier;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;
import kz.madiyar.plugins.host.PluginFailureException;
import kz.madiyar.plugins.host.PluginTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaultBarrierTest {

    private final ClassLoader loader = getClass().getClassLoader();
    private final FaultBarrier barrier = new FaultBarrier("p", Duration.ofMillis(200), loader);

    @AfterEach
    void closeBarrier() {
        barrier.close();
    }

    @Test
    void returnsResultOfTheCall() {
        assertEquals("ok", barrier.call("handle", () -> "ok"));
    }

    @Test
    void runsOnPluginThreadNotOnCallerThread() {
        Thread caller = Thread.currentThread();
        Thread worker = barrier.call("handle", Thread::currentThread);
        assertNotSame(caller, worker);
        assertEquals("plugin-p", worker.getName());
        assertTrue(worker.isDaemon());
        assertSame(loader, worker.getContextClassLoader());
    }

    @Test
    void runtimeExceptionBecomesPluginFailure() {
        PluginFailureException e = assertThrows(PluginFailureException.class,
                () -> barrier.call("handle", () -> {
                    throw new IllegalStateException("bad");
                }));
        assertEquals("p", e.pluginId());
        assertEquals("Plugin 'p': handle failed: java.lang.IllegalStateException: bad", e.getMessage());
        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertFalse(barrier.isQuarantined());
    }

    @Test
    void errorsLikeOutOfMemoryAreContainedToo() {
        PluginFailureException e = assertThrows(PluginFailureException.class,
                () -> barrier.call("handle", () -> {
                    throw new OutOfMemoryError("simulated");
                }));
        assertInstanceOf(OutOfMemoryError.class, e.getCause());
        assertEquals("ok", barrier.call("handle", () -> "ok"), "barrier still works after an Error");
    }

    @Test
    void slowCallTimesOutAndQuarantinesThePlugin() {
        PluginTimeoutException e = assertThrows(PluginTimeoutException.class,
                () -> barrier.call("handle", () -> {
                    Thread.sleep(10_000);
                    return "too late";
                }));
        assertEquals("Plugin 'p': handle timed out after 200 ms; plugin quarantined", e.getMessage());
        assertTrue(barrier.isQuarantined());

        PluginFailureException next = assertThrows(PluginFailureException.class,
                () -> barrier.call("handle", () -> "x"));
        assertEquals("Plugin 'p': handle rejected: plugin is quarantined after a timeout", next.getMessage());
    }

    @Test
    void callAfterCloseIsRejected() {
        barrier.close();
        PluginFailureException e = assertThrows(PluginFailureException.class,
                () -> barrier.call("handle", () -> "x"));
        assertEquals("Plugin 'p': handle rejected: plugin runtime is closed", e.getMessage());
    }

    @Test
    void closeStopsThePluginThread() throws Exception {
        Thread worker = barrier.call("handle", Thread::currentThread);
        barrier.close();
        worker.join(1_000);
        assertFalse(worker.isAlive());
    }

    @Test
    void interruptedCallerGetsPluginFailureAndKeepsInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            PluginFailureException e = assertThrows(PluginFailureException.class,
                    () -> barrier.call("handle", () -> {
                        Thread.sleep(1_000);
                        return "x";
                    }));
            assertEquals("Plugin 'p': handle interrupted", e.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // clear the flag for other tests
        }
    }

    // ------------------------------------------------------------- the Proxy

    /** Records what the proxy passes to the real plugin. */
    private static class RecordingPlugin implements Plugin {
        final List<String> calls = new ArrayList<>();
        final AtomicReference<Thread> thread = new AtomicReference<>();

        @Override
        public void start(PluginContext context) {
            calls.add("start:" + context.pluginId());
            thread.set(Thread.currentThread());
        }

        @Override
        public String handle(String request) {
            calls.add("handle:" + request);
            return request.toUpperCase();
        }

        @Override
        public void stop() {
            calls.add("stop");
        }
    }

    private static final PluginContext CONTEXT = new PluginContext() {
        @Override
        public String pluginId() {
            return "p";
        }

        @Override
        public void log(String message) {
            // silent
        }
    };

    @Test
    void proxyForwardsEveryCallThroughTheBarrier() {
        RecordingPlugin real = new RecordingPlugin();
        Plugin proxy = new FaultBarrierPlugin(real, barrier);

        proxy.start(CONTEXT);
        assertEquals("HI", proxy.handle("hi"));
        proxy.stop();

        assertEquals(List.of("start:p", "handle:hi", "stop"), real.calls);
        assertEquals("plugin-p", real.thread.get().getName());
    }

    @Test
    void proxyDoesNotCallStopOnQuarantinedPlugin() {
        RecordingPlugin real = new RecordingPlugin();
        Plugin proxy = new FaultBarrierPlugin(real, barrier);
        assertThrows(PluginTimeoutException.class, () -> barrier.call("handle", () -> {
            Thread.sleep(10_000);
            return null;
        }));

        proxy.stop();   // must not throw and must not reach the plugin
        assertEquals(List.of(), real.calls);
    }

    @Test
    void proxyTurnsPluginExceptionIntoPluginFailure() {
        Plugin failing = new RecordingPlugin() {
            @Override
            public String handle(String request) {
                throw new IllegalArgumentException("nope");
            }
        };
        Plugin proxy = new FaultBarrierPlugin(failing, barrier);
        assertThrows(PluginFailureException.class, () -> proxy.handle("x"));
    }
}
