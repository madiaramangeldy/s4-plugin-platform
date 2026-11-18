package kz.madiyar.plugins.host.loading;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;
import kz.madiyar.plugins.host.PluginDescriptor;
import kz.madiyar.plugins.samples.HelloPlugin;
import kz.madiyar.plugins.testsupport.TestPluginJars;
import kz.madiyar.plugins.testsupport.sneaky.SneakyPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginClassLoaderTest {

    @TempDir
    Path dir;

    private PluginClassLoader loaderFor(Path jar, String id) throws Exception {
        return new PluginClassLoader(id, jar.toUri().toURL(), Plugin.class.getClassLoader());
    }

    private Path helloJar() {
        return TestPluginJars.jar(dir, "hello.jar").descriptor("hello", "1.0", 2, HelloPlugin.class).build();
    }

    private static final PluginContext SILENT = new PluginContext() {
        @Override
        public String pluginId() {
            return "test";
        }

        @Override
        public void log(String message) {
            // silent
        }
    };

    @Test
    void pluginClassComesFromItsOwnLoaderNotFromHostClasspath() throws Exception {
        try (PluginClassLoader loader = loaderFor(helloJar(), "hello")) {
            Class<?> cls = loader.loadClass(HelloPlugin.class.getName());
            assertSame(loader, cls.getClassLoader());
            assertNotSame(HelloPlugin.class, cls);
        }
    }

    @Test
    void apiIsSharedWithHostSoPluginCanBeUsedAsPlugin() throws Exception {
        try (PluginClassLoader loader = loaderFor(helloJar(), "hello")) {
            assertSame(Plugin.class, loader.loadClass(Plugin.class.getName()));
            Object instance = loader.loadClass(HelloPlugin.class.getName()).getDeclaredConstructor().newInstance();
            Plugin plugin = (Plugin) instance;
            plugin.start(SILENT);
            assertEquals("Hello, Madiyar!", plugin.handle("Madiyar"));
        }
    }

    @Test
    void hostInternalClassesAreInvisible() throws Exception {
        try (PluginClassLoader loader = loaderFor(helloJar(), "hello")) {
            ClassNotFoundException e = assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass(PluginDescriptor.class.getName()));
            assertTrue(e.getMessage().contains("internal to the host"));
        }
    }

    @Test
    void otherHostClasspathClassesAreInvisible() throws Exception {
        try (PluginClassLoader loader = loaderFor(helloJar(), "hello")) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(TestPluginJars.class.getName()));
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Test.class.getName()));
        }
    }

    @Test
    void jdkClassesAreVisible() throws Exception {
        try (PluginClassLoader loader = loaderFor(helloJar(), "hello")) {
            assertSame(java.util.ArrayList.class, loader.loadClass("java.util.ArrayList"));
        }
    }

    @Test
    void twoPluginsGetDifferentCopiesOfTheSameClass() throws Exception {
        Path jar = helloJar();
        try (PluginClassLoader a = loaderFor(jar, "a"); PluginClassLoader b = loaderFor(jar, "b")) {
            assertNotSame(a.loadClass(HelloPlugin.class.getName()), b.loadClass(HelloPlugin.class.getName()));
        }
    }

    @Test
    void loaderIsNamedAfterPlugin() throws Exception {
        try (PluginClassLoader loader = loaderFor(helloJar(), "hello")) {
            assertEquals("plugin:hello", loader.getName());
        }
    }

    @Test
    void pluginThatUsesHostInternalsFailsAtRuntime() throws Exception {
        Path jar = TestPluginJars.jar(dir, "sneaky.jar").descriptor("sneaky", "1.0", 2, SneakyPlugin.class).build();
        try (PluginClassLoader loader = loaderFor(jar, "sneaky")) {
            Plugin plugin = (Plugin) loader.loadClass(SneakyPlugin.class.getName())
                    .getDeclaredConstructor().newInstance();
            assertThrows(NoClassDefFoundError.class, () -> plugin.handle("x"));
        }
    }
}
