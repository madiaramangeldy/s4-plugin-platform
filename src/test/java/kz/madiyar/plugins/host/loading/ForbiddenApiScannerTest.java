package kz.madiyar.plugins.host.loading;

import kz.madiyar.plugins.host.PluginFailureException;
import kz.madiyar.plugins.host.PluginPlatformException;
import kz.madiyar.plugins.samples.EvilPlugin;
import kz.madiyar.plugins.samples.ExitPlugin;
import kz.madiyar.plugins.samples.HelloPlugin;
import kz.madiyar.plugins.testsupport.TestPluginJars;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForbiddenApiScannerTest {

    @TempDir
    Path dir;

    private final ForbiddenApiScanner scanner = new ForbiddenApiScanner();

    private static byte[] bytesOf(Class<?> cls) throws IOException {
        try (InputStream in = cls.getResourceAsStream("/" + cls.getName().replace('.', '/') + ".class")) {
            return in.readAllBytes();
        }
    }

    @Test
    void findsSystemExitAndRuntimeHalt() throws Exception {
        List<String> calls = ForbiddenApiScanner.forbiddenCalls(bytesOf(ExitPlugin.class));
        assertTrue(calls.contains("java/lang/System.exit"), calls.toString());
        assertTrue(calls.contains("java/lang/Runtime.halt"), calls.toString());
    }

    @Test
    void cleanPluginsHaveNoForbiddenCalls() throws Exception {
        assertEquals(List.of(), ForbiddenApiScanner.forbiddenCalls(bytesOf(HelloPlugin.class)));
        assertEquals(List.of(), ForbiddenApiScanner.forbiddenCalls(bytesOf(EvilPlugin.class)));
    }

    /** This test class itself is full of constants of every kind — a good parser stress test. */
    @Test
    void parsesClassesWithManyConstantTypes() throws Exception {
        assertEquals(List.of(), ForbiddenApiScanner.forbiddenCalls(bytesOf(ForbiddenApiScannerTest.class)));
        assertEquals(List.of(), ForbiddenApiScanner.forbiddenCalls(bytesOf(ConstantsZoo.class)));
    }

    @SuppressWarnings("unused")
    private static final class ConstantsZoo {
        static final long L = 1234567890123L;
        static final double D = 3.14159;
        static final float F = 2.5f;
        static final int I = 100_000;
        static final String S = "text";

        Object all() {
            Runnable r = () -> System.out.println(L + D + F + I + S);
            return r;
        }
    }

    @Test
    void notAClassFileIsRejected() {
        IOException e = assertThrows(IOException.class,
                () -> ForbiddenApiScanner.forbiddenCalls(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 0, 0}));
        assertEquals("not a class file", e.getMessage());
    }

    @Test
    void unknownConstantTagIsRejected() {
        byte[] bytes = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 61, 0, 2, 99};
        IOException e = assertThrows(IOException.class, () -> ForbiddenApiScanner.forbiddenCalls(bytes));
        assertEquals("unknown constant pool tag 99 at index 1", e.getMessage());
    }

    @Test
    void jarWithExitIsRejectedWithClearMessage() {
        Path jar = TestPluginJars.jar(dir, "exit.jar").descriptor("exit", "1.0", 2, ExitPlugin.class).build();
        PluginFailureException e = assertThrows(PluginFailureException.class, () -> scanner.check(jar, "exit"));
        assertEquals("exit", e.pluginId());
        assertTrue(e.getMessage().startsWith("Plugin 'exit': rejected, forbidden API used: "
                + "kz/madiyar/plugins/samples/ExitPlugin.class calls java/lang/"), e.getMessage());
    }

    @Test
    void cleanJarPasses() {
        Path jar = TestPluginJars.jar(dir, "hello.jar").descriptor("hello", "1.0", 2, HelloPlugin.class).build();
        assertDoesNotThrow(() -> scanner.check(jar, "hello"));
    }

    @Test
    void brokenJarIsReported() throws Exception {
        Path notJar = Files.writeString(dir.resolve("broken.jar"), "nope");
        PluginPlatformException e = assertThrows(PluginPlatformException.class, () -> scanner.check(notJar, "x"));
        assertEquals("broken.jar: cannot read JAR", e.getMessage());
    }
}
