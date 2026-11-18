package kz.madiyar.plugins.host.runtime;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;
import kz.madiyar.plugins.api.v1.LegacyPlugin;
import kz.madiyar.plugins.host.PluginDescriptor;
import kz.madiyar.plugins.host.PluginPlatformException;
import kz.madiyar.plugins.samples.HelloPlugin;
import kz.madiyar.plugins.samples.LegacyUpperPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeFactoryTest {

    private final List<String> logs = new ArrayList<>();

    private PluginContext context(PluginRuntimeFactory factory, String id) {
        return factory.createContext(new PluginDescriptor(id, "1.0", factory.apiVersion(), "x.P", List.of()),
                (pluginId, message) -> logs.add(pluginId + ": " + message));
    }

    // ---------------------------------------------------------------- v2

    @Test
    void v2FactoryCreatesPluginDirectly() {
        V2RuntimeFactory factory = new V2RuntimeFactory();
        assertEquals(2, factory.apiVersion());
        Plugin plugin = factory.createPlugin(HelloPlugin.class);
        assertInstanceOf(HelloPlugin.class, plugin);
    }

    @Test
    void v2ContextLogsWithoutPrefix() {
        PluginContext ctx = context(new V2RuntimeFactory(), "hello");
        assertEquals("hello", ctx.pluginId());
        ctx.log("hi");
        assertEquals(List.of("hello: hi"), logs);
    }

    @Test
    void v2FactoryRejectsClassThatIsNotAPlugin() {
        PluginPlatformException e = assertThrows(PluginPlatformException.class,
                () -> new V2RuntimeFactory().createPlugin(LegacyUpperPlugin.class));
        assertEquals("Main class " + LegacyUpperPlugin.class.getName()
                + " does not implement " + Plugin.class.getName() + " (api=2)", e.getMessage());
    }

    // ---------------------------------------------------------------- v1

    @Test
    void v1FactoryWrapsLegacyPluginInAdapter() {
        V1RuntimeFactory factory = new V1RuntimeFactory();
        assertEquals(1, factory.apiVersion());
        Plugin plugin = factory.createPlugin(LegacyUpperPlugin.class);
        assertInstanceOf(LegacyPluginAdapter.class, plugin);

        plugin.start(context(factory, "legacy"));
        assertEquals("ABC", plugin.handle("abc"));
        plugin.stop();
        assertEquals(List.of("legacy: [api-v1] started through adapter", "legacy: [api-v1] stopped through adapter"),
                logs);
    }

    @Test
    void v1FactoryRejectsV2Plugin() {
        PluginPlatformException e = assertThrows(PluginPlatformException.class,
                () -> new V1RuntimeFactory().createPlugin(HelloPlugin.class));
        assertEquals("Main class " + HelloPlugin.class.getName()
                + " does not implement " + LegacyPlugin.class.getName() + " (api=1)", e.getMessage());
    }

    // ---------------------------------------------------------------- instantiation errors

    public static class NoDefaultConstructor implements Plugin {
        public NoDefaultConstructor(String unused) {
        }

        @Override
        public void start(PluginContext context) {
        }

        @Override
        public String handle(String request) {
            return request;
        }

        @Override
        public void stop() {
        }
    }

    public static class FailingConstructor extends NoDefaultConstructor {
        public FailingConstructor() {
            super(null);
            throw new IllegalStateException("cannot construct");
        }
    }

    @Test
    void missingNoArgConstructorIsReported() {
        PluginPlatformException e = assertThrows(PluginPlatformException.class,
                () -> new V2RuntimeFactory().createPlugin(NoDefaultConstructor.class));
        assertEquals("Cannot create " + NoDefaultConstructor.class.getName()
                + ": a public no-argument constructor is required", e.getMessage());
    }

    @Test
    void exceptionInConstructorIsReportedWithRealCause() {
        PluginPlatformException e = assertThrows(PluginPlatformException.class,
                () -> new V2RuntimeFactory().createPlugin(FailingConstructor.class));
        assertEquals("Constructor of " + FailingConstructor.class.getName() + " failed", e.getMessage());
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    // ---------------------------------------------------------------- adapter

    @Test
    void adapterMapsEveryMethodOfV2ToV1() {
        List<String> calls = new ArrayList<>();
        LegacyPlugin legacy = new LegacyPlugin() {
            @Override
            public void init() {
                calls.add("init");
            }

            @Override
            public String process(String input) {
                calls.add("process:" + input);
                return "r";
            }

            @Override
            public void destroy() {
                calls.add("destroy");
            }
        };
        Plugin adapter = new LegacyPluginAdapter(legacy);
        adapter.start(context(new V1RuntimeFactory(), "legacy"));
        assertEquals("r", adapter.handle("q"));
        adapter.stop();
        assertEquals(List.of("init", "process:q", "destroy"), calls);
    }

    @Test
    void adapterStopWithoutStartDoesNotFail() {
        LegacyUpperPlugin legacy = new LegacyUpperPlugin();
        new LegacyPluginAdapter(legacy).stop();
        assertThrows(IllegalStateException.class, () -> legacy.process("x"));
    }
}
