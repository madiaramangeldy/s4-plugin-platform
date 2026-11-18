package kz.madiyar.plugins.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Builds real plugin JAR files inside a test's temp folder.
 * Class bytes are copied from the compiled test classes, so the JAR contains
 * exactly what a third-party plugin JAR would contain.
 *
 * <pre>
 * Path jar = TestPluginJars.jar(tempDir, "hello-1.0.jar")
 *         .descriptor("hello", "1.0", 2, HelloPlugin.class)
 *         .resource("greeting.txt", "Hi")
 *         .build();
 * </pre>
 */
public final class TestPluginJars {

    private final Path file;
    private final List<Class<?>> classes = new ArrayList<>();
    private final Map<String, String> resources = new LinkedHashMap<>();

    private TestPluginJars(Path file) {
        this.file = file;
    }

    public static TestPluginJars jar(Path dir, String fileName) {
        return new TestPluginJars(dir.resolve(fileName));
    }

    /** Writes plugin.properties and adds the main class to the JAR. */
    public TestPluginJars descriptor(String id, String version, int api, Class<?> mainClass, String... dependencies) {
        resources.put("plugin.properties",
                "id=" + id + "\n"
                        + "version=" + version + "\n"
                        + "api=" + api + "\n"
                        + "main=" + mainClass.getName() + "\n"
                        + "dependencies=" + String.join(",", dependencies) + "\n");
        return withClass(mainClass);
    }

    public TestPluginJars withClass(Class<?> cls) {
        classes.add(cls);
        return this;
    }

    public TestPluginJars resource(String name, String content) {
        resources.put(name, content);
        return this;
    }

    public Path build() {
        try (OutputStream out = Files.newOutputStream(file);
             JarOutputStream jar = new JarOutputStream(out)) {
            for (Map.Entry<String, String> r : resources.entrySet()) {
                jar.putNextEntry(new JarEntry(r.getKey()));
                jar.write(r.getValue().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
            for (Class<?> cls : classes) {
                String path = cls.getName().replace('.', '/') + ".class";
                jar.putNextEntry(new JarEntry(path));
                jar.write(classBytes(cls, path));
                jar.closeEntry();
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] classBytes(Class<?> cls, String path) throws IOException {
        try (InputStream in = cls.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("class file not found: " + path);
            }
            return in.readAllBytes();
        }
    }
}
