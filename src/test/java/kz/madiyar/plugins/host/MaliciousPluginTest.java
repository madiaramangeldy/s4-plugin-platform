package kz.madiyar.plugins.host;

import kz.madiyar.plugins.host.lifecycle.PluginState;
import kz.madiyar.plugins.samples.EvilPlugin;
import kz.madiyar.plugins.samples.HelloPlugin;
import kz.madiyar.plugins.testsupport.TestPluginJars;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance criterion: "a malicious plugin cannot stop the host".
 * EvilPlugin runs in the same host as a normal plugin; after every attack the host still serves requests.
 * (The System.exit attack is in PluginManagerTest.pluginCallingSystemExitIsRejectedBeforeAnyCodeRuns.)
 */
@Tag("chaos")
@Timeout(10)
class MaliciousPluginTest {

    @TempDir
    Path dir;

    private PluginManager manager;

    @BeforeEach
    void startHostWithGoodAndEvilPlugin() {
        manager = PluginManager.createDefault(Duration.ofMillis(300), (id, msg) -> { });
        Path good = TestPluginJars.jar(dir, "hello.jar").descriptor("hello", "1", 2, HelloPlugin.class).build();
        Path evil = TestPluginJars.jar(dir, "evil.jar").descriptor("evil", "6.6.6", 2, EvilPlugin.class).build();
        manager.install(List.of(good, evil));
        assertEquals(List.of("evil", "hello"), manager.startAll());
    }

    @AfterEach
    void closeHost() {
        manager.close();
    }

    @ParameterizedTest(name = "{0} is contained")
    @CsvSource({
            "boom, java.lang.IllegalStateException",
            "oom,  java.lang.OutOfMemoryError",
            "deep, java.lang.StackOverflowError"
    })
    void crashingRequestIsContainedAndPluginStaysUsable(String attack, Class<? extends Throwable> expected) {
        PluginFailureException e = assertThrows(PluginFailureException.class, () -> manager.call("evil", attack));
        assertInstanceOf(expected, e.getCause());

        assertEquals("Hello, still alive!", manager.call("hello", "still alive"));
        assertEquals("evil says: hi", manager.call("evil", "hi"), "a crash is not a hang: the plugin keeps working");
    }

    /**
     * One test on purpose: a looping plugin thread cannot be killed in Java, so every loop
     * attack leaves one busy daemon thread until the JVM exits. We keep it to exactly one.
     */
    @Test
    void infiniteLoopTimesOutPluginIsStoppedAndQuarantined() {
        long startedAt = System.nanoTime();
        assertThrows(PluginTimeoutException.class, () -> manager.call("evil", "loop"));
        long tookMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertTrue(tookMs < 2_000, "host waited only about the timeout, took " + tookMs + " ms");
        assertEquals("Hello, still alive!", manager.call("hello", "still alive"));
        assertSame(PluginState.STOPPED, manager.state("evil"));
        assertEquals(List.of("hello"), manager.activePlugins());

        PluginFailureException e = assertThrows(PluginFailureException.class, () -> manager.start("evil"));
        assertEquals("Plugin 'evil': start rejected: plugin is quarantined after a timeout", e.getMessage());
        assertSame(PluginState.STOPPED, manager.state("evil"));
    }
}
