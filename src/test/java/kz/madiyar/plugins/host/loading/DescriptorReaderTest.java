package kz.madiyar.plugins.host.loading;

import kz.madiyar.plugins.host.PluginDescriptor;
import kz.madiyar.plugins.host.PluginPlatformException;
import kz.madiyar.plugins.samples.HelloPlugin;
import kz.madiyar.plugins.testsupport.TestPluginJars;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DescriptorReaderTest {

    @TempDir
    Path dir;

    private final DescriptorReader reader = new DescriptorReader();

    @Test
    void readsAllFields() {
        Path jar = TestPluginJars.jar(dir, "hello.jar")
                .descriptor("hello", "1.2.0", 2, HelloPlugin.class, "core", "log")
                .build();
        PluginDescriptor d = reader.read(jar);
        assertEquals(new PluginDescriptor("hello", "1.2.0", 2, HelloPlugin.class.getName(), List.of("core", "log")), d);
    }

    @Test
    void noDependenciesGivesEmptyList() {
        Path jar = TestPluginJars.jar(dir, "hello.jar").descriptor("hello", "1.0", 2, HelloPlugin.class).build();
        assertEquals(List.of(), reader.read(jar).dependencies());
    }

    @Test
    void spacesAndEmptyItemsInDependenciesAreIgnored() {
        Path jar = TestPluginJars.jar(dir, "p.jar")
                .resource("plugin.properties", "id=p\nversion=1\napi=1\nmain=x.P\ndependencies= a , ,b ,\n")
                .build();
        assertEquals(List.of("a", "b"), reader.read(jar).dependencies());
    }

    @Test
    void missingDescriptorIsReported() {
        Path jar = TestPluginJars.jar(dir, "empty.jar").resource("readme.txt", "hi").build();
        PluginPlatformException e = assertThrows(PluginPlatformException.class, () -> reader.read(jar));
        assertEquals("empty.jar: plugin.properties not found", e.getMessage());
    }

    @Test
    void nonNumericApiIsReported() {
        Path jar = TestPluginJars.jar(dir, "bad.jar")
                .resource("plugin.properties", "id=p\nversion=1\napi=two\nmain=x.P\n")
                .build();
        PluginPlatformException e = assertThrows(PluginPlatformException.class, () -> reader.read(jar));
        assertEquals("bad.jar: 'api' must be 1 or 2, got 'two'", e.getMessage());
    }

    @Test
    void missingApiIsReported() {
        Path jar = TestPluginJars.jar(dir, "bad.jar")
                .resource("plugin.properties", "id=p\nversion=1\nmain=x.P\n")
                .build();
        PluginPlatformException e = assertThrows(PluginPlatformException.class, () -> reader.read(jar));
        assertEquals("bad.jar: 'api' must be 1 or 2, got 'null'", e.getMessage());
    }

    @Test
    void notAJarIsReported() throws Exception {
        Path notJar = Files.writeString(dir.resolve("broken.jar"), "this is not a zip");
        PluginPlatformException e = assertThrows(PluginPlatformException.class, () -> reader.read(notJar));
        assertEquals("broken.jar: cannot read JAR", e.getMessage());
    }
}
