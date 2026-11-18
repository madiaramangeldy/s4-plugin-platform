package kz.madiyar.plugins.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginDescriptorTest {

    @Test
    void validDescriptorKeepsValues() {
        PluginDescriptor d = new PluginDescriptor("hello", "1.2.0", 2, "x.Hello", List.of("core"));
        assertEquals("hello", d.id());
        assertEquals("1.2.0", d.version());
        assertEquals(2, d.apiVersion());
        assertEquals("x.Hello", d.mainClass());
        assertEquals(List.of("core"), d.dependencies());
    }

    @Test
    void dependenciesAreCopiedSoOutsideChangesDoNotLeakIn() {
        List<String> deps = new ArrayList<>(List.of("core"));
        PluginDescriptor d = new PluginDescriptor("hello", "1.0", 2, "x.Hello", deps);
        deps.add("other");
        assertEquals(List.of("core"), d.dependencies());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, -1})
    void unsupportedApiVersionIsRejected(int api) {
        PluginPlatformException e = assertThrows(PluginPlatformException.class,
                () -> new PluginDescriptor("p", "1.0", api, "x.P", List.of()));
        assertEquals("Plugin 'p': unsupported api version " + api + " (expected 1 or 2)", e.getMessage());
    }

    @Test
    void bothApiVersionsAreAccepted() {
        assertEquals(1, new PluginDescriptor("p", "1.0", 1, "x.P", List.of()).apiVersion());
        assertEquals(2, new PluginDescriptor("p", "1.0", 2, "x.P", List.of()).apiVersion());
    }

    @Test
    void emptyFieldsAreRejected() {
        assertEquals("Plugin descriptor: 'id' must not be empty",
                assertThrows(PluginPlatformException.class,
                        () -> new PluginDescriptor(" ", "1.0", 2, "x.P", List.of())).getMessage());
        assertEquals("Plugin descriptor: 'version' must not be empty",
                assertThrows(PluginPlatformException.class,
                        () -> new PluginDescriptor("p", null, 2, "x.P", List.of())).getMessage());
        assertEquals("Plugin descriptor: 'mainClass' must not be empty",
                assertThrows(PluginPlatformException.class,
                        () -> new PluginDescriptor("p", "1.0", 2, "", List.of())).getMessage());
    }
}
