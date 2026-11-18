package kz.madiyar.plugins.host;

import kz.madiyar.plugins.samples.HelloPlugin;
import kz.madiyar.plugins.testsupport.TestPluginJars;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The "hard part" of S4: after hot reload the OLD ClassLoader must be garbage collected
 * (no memory leak). A WeakReference does not keep an object alive, so once nothing else
 * points to the old loader the GC clears the reference.
 */
@Tag("gc")
class HotReloadMemoryTest {

    @TempDir
    Path dir;

    @Test
    void oldClassLoaderIsCollectedAfterReload() throws Exception {
        try (PluginManager manager = PluginManager.createDefault(Duration.ofSeconds(2), (id, msg) -> { })) {
            Path v1 = TestPluginJars.jar(dir, "hello-1.jar").descriptor("hello", "1.0", 2, HelloPlugin.class).build();
            Path v2 = TestPluginJars.jar(dir, "hello-2.jar").descriptor("hello", "2.0", 2, HelloPlugin.class)
                    .resource("greeting.txt", "Salem").build();
            manager.install(List.of(v1));
            manager.startAll();
            assertEquals("Hello, x!", manager.call("hello", "x"));

            WeakReference<ClassLoader> oldLoader = new WeakReference<>(manager.classLoaderOf("hello"));

            manager.reload("hello", v2);
            assertEquals("Salem, x!", manager.call("hello", "x"));

            for (int i = 0; i < 50 && oldLoader.get() != null; i++) {
                System.gc();
                Thread.sleep(20);
            }
            assertNull(oldLoader.get(), "old plugin ClassLoader leaked: something still references it");
        }
    }

    /** Ten reloads in a row: none of the old loaders may stay in memory. */
    @Test
    void repeatedReloadsDoNotAccumulateLoaders() throws Exception {
        try (PluginManager manager = PluginManager.createDefault(Duration.ofSeconds(2), (id, msg) -> { })) {
            manager.install(List.of(
                    TestPluginJars.jar(dir, "h0.jar").descriptor("hello", "0", 2, HelloPlugin.class).build()));
            manager.startAll();

            List<WeakReference<ClassLoader>> old = new java.util.ArrayList<>();
            for (int v = 1; v <= 10; v++) {
                old.add(new WeakReference<>(manager.classLoaderOf("hello")));
                manager.reload("hello", TestPluginJars.jar(dir, "h" + v + ".jar")
                        .descriptor("hello", String.valueOf(v), 2, HelloPlugin.class).build());
            }
            assertEquals("10", manager.version("hello"));

            for (int i = 0; i < 50 && old.stream().anyMatch(r -> r.get() != null); i++) {
                System.gc();
                Thread.sleep(20);
            }
            assertEquals(0, old.stream().filter(r -> r.get() != null).count(), "leaked loaders");
        }
    }
}
