package kz.madiyar.plugins.demo;

import kz.madiyar.plugins.host.PluginManager;
import kz.madiyar.plugins.host.PluginPlatformException;
import kz.madiyar.plugins.samples.EvilPlugin;
import kz.madiyar.plugins.samples.ExitPlugin;
import kz.madiyar.plugins.samples.HelloPlugin;
import kz.madiyar.plugins.samples.LegacyUpperPlugin;
import kz.madiyar.plugins.testsupport.TestPluginJars;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Demo for the defense: the main scenario of S4 step by step, with console output.
 *
 * <pre>
 * mvn test-compile
 * java -cp "target/classes;target/test-classes" kz.madiyar.plugins.demo.PlatformDemo     (Windows)
 * java -cp "target/classes:target/test-classes" kz.madiyar.plugins.demo.PlatformDemo     (Linux/macOS)
 * </pre>
 */
public final class PlatformDemo {

    private PlatformDemo() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createDirectories(Path.of("target", "demo-plugins"));

        step("1. Build sample plugin JARs into " + dir);
        Path hello = TestPluginJars.jar(dir, "hello-1.0.jar")
                .descriptor("hello", "1.0", 2, HelloPlugin.class).build();
        Path legacy = TestPluginJars.jar(dir, "legacy-1.0.jar")
                .descriptor("legacy", "1.0", 1, LegacyUpperPlugin.class, "hello").build();
        Path evil = TestPluginJars.jar(dir, "evil-6.6.6.jar")
                .descriptor("evil", "6.6.6", 2, EvilPlugin.class).build();
        Path exit = TestPluginJars.jar(dir, "exit-1.0.jar")
                .descriptor("exit", "1.0", 2, ExitPlugin.class).build();
        Path helloV2 = TestPluginJars.jar(dir, "hello-2.0.jar")
                .descriptor("hello", "2.0", 2, HelloPlugin.class).resource("greeting.txt", "Salem").build();
        System.out.println("   hello (api v2), legacy (api v1, depends on hello), evil, exit, hello 2.0");

        try (PluginManager manager = PluginManager.createDefault(Duration.ofMillis(500),
                (id, msg) -> System.out.println("   [plugin:" + id + "] " + msg))) {

            step("2. Install (R1: dependencies first, R3: INSTALLED -> RESOLVED)");
            List<String> order = manager.install(List.of(legacy, evil, hello));
            System.out.println("   load order: " + order);
            order.forEach(id -> System.out.println("   " + id + " -> " + manager.state(id)));

            step("3. Start all (R3: RESOLVED -> ACTIVE)");
            System.out.println("   active: " + manager.startAll());

            step("4. Call plugins (R4: v1 plugin works through the Adapter)");
            System.out.println("   hello  <- 'Madiyar'  => " + manager.call("hello", "Madiyar"));
            System.out.println("   legacy <- 'vtipo'    => " + manager.call("legacy", "vtipo"));

            step("5. Evil plugin attacks the host (R5: Proxy / fault barrier)");
            for (String attack : List.of("boom", "oom", "deep", "loop")) {
                try {
                    manager.call("evil", attack);
                } catch (PluginPlatformException e) {
                    System.out.println("   " + pad(attack) + " contained: " + e.getMessage());
                }
            }
            System.out.println("   evil state now: " + manager.state("evil"));
            System.out.println("   host still alive: hello => " + manager.call("hello", "after attack"));

            step("6. Plugin that calls System.exit (rejected before any code runs)");
            try {
                manager.install(List.of(exit));
            } catch (PluginPlatformException e) {
                System.out.println("   " + e.getMessage());
            }

            step("7. Hot reload hello 1.0 -> 2.0 without restarting the host (R6)");
            System.out.println("   before: " + manager.call("hello", "Madiyar") + "  (v" + manager.version("hello") + ")");
            manager.reload("hello", helloV2);
            System.out.println("   after:  " + manager.call("hello", "Madiyar") + "  (v" + manager.version("hello") + ")");
            System.out.println("   legacy still works: " + manager.call("legacy", "ok"));

            step("8. Shutdown: stop in reverse order");
        }
        System.out.println();
        System.out.println("Demo finished. The host survived every attack.");
    }

    private static void step(String title) {
        System.out.println();
        System.out.println("== " + title);
    }

    private static String pad(String s) {
        return String.format("%-5s", s);
    }
}
