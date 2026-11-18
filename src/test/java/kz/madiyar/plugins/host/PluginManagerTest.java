package kz.madiyar.plugins.host;

import kz.madiyar.plugins.host.lifecycle.IllegalTransitionException;
import kz.madiyar.plugins.host.lifecycle.PluginState;
import kz.madiyar.plugins.samples.ExitPlugin;
import kz.madiyar.plugins.samples.HelloPlugin;
import kz.madiyar.plugins.samples.LegacyUpperPlugin;
import kz.madiyar.plugins.testsupport.BrokenStartPlugin;
import kz.madiyar.plugins.testsupport.TestPluginJars;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManagerTest {

    @TempDir
    Path dir;

    private final List<String> logs = new CopyOnWriteArrayList<>();
    private final PluginManager manager =
            PluginManager.createDefault(Duration.ofMillis(500), (id, msg) -> logs.add(id + ": " + msg));

    @AfterEach
    void closeManager() {
        manager.close();
    }

    private Path hello(String id, String version, String... deps) {
        return TestPluginJars.jar(dir, id + "-" + version + ".jar")
                .descriptor(id, version, 2, HelloPlugin.class, deps).build();
    }

    private Path legacy(String id, String... deps) {
        return TestPluginJars.jar(dir, id + ".jar").descriptor(id, "1.0", 1, LegacyUpperPlugin.class, deps).build();
    }

    // ---------------------------------------------------------------- install (R1)

    @Test
    void installLoadsInDependencyOrderAndResolves() {
        List<String> ids = manager.install(List.of(hello("app", "1", "log"), hello("log", "1", "core"), hello("core", "1")));
        assertEquals(List.of("core", "log", "app"), ids);
        assertEquals(List.of("core", "log", "app"), manager.installedPlugins());
        ids.forEach(id -> assertSame(PluginState.RESOLVED, manager.state(id)));
    }

    @Test
    void cycleIsRejectedAndNothingIsInstalled() {
        DependencyCycleException e = assertThrows(DependencyCycleException.class,
                () -> manager.install(List.of(hello("a", "1", "b"), hello("b", "1", "a"), hello("ok", "1"))));
        assertEquals(List.of("a", "b", "a"), e.cycle());
        assertEquals(List.of(), manager.installedPlugins());
    }

    @Test
    void missingDependencyIsRejected() {
        PluginPlatformException e = assertThrows(PluginPlatformException.class,
                () -> manager.install(List.of(hello("a", "1", "ghost"))));
        assertEquals("Plugin 'a' depends on missing plugin 'ghost'", e.getMessage());
    }

    @Test
    void dependencyMayBeAPluginInstalledEarlier() {
        manager.install(List.of(hello("core", "1")));
        assertEquals(List.of("app"), manager.install(List.of(hello("app", "1", "core"))));
        assertEquals(List.of("core", "app"), manager.installedPlugins());
    }

    @Test
    void sameIdTwiceIsRejected() {
        manager.install(List.of(hello("a", "1")));
        PluginPlatformException e = assertThrows(PluginPlatformException.class,
                () -> manager.install(List.of(hello("a", "2"))));
        assertEquals("Plugin 'a' is already installed", e.getMessage());

        assertThrows(PluginPlatformException.class,
                () -> manager.install(List.of(hello("b", "1"), hello("b", "2"))));
    }

    @Test
    void pluginCallingSystemExitIsRejectedBeforeAnyCodeRuns() {
        Path exit = TestPluginJars.jar(dir, "exit.jar").descriptor("exit", "1", 2, ExitPlugin.class).build();
        PluginFailureException e = assertThrows(PluginFailureException.class,
                () -> manager.install(List.of(hello("good", "1"), exit)));
        assertTrue(e.getMessage().contains("java/lang/System.exit"), e.getMessage());
        assertEquals(List.of(), manager.installedPlugins(), "all-or-nothing: the good plugin is not installed either");
    }

    @Test
    void wrongMainClassFailsInstallAndReleasesAlreadyLoadedOnes() {
        Path wrong = TestPluginJars.jar(dir, "wrong.jar").descriptor("wrong", "1", 2, LegacyUpperPlugin.class).build();
        PluginFailureException e = assertThrows(PluginFailureException.class,
                () -> manager.install(List.of(hello("a", "1"), wrong)));
        assertTrue(e.getMessage().contains("does not implement"), e.getMessage());
        assertEquals(List.of(), manager.installedPlugins());
    }

    @Test
    void missingMainClassIsReported() {
        Path jar = TestPluginJars.jar(dir, "nomain.jar")
                .resource("plugin.properties", "id=nomain\nversion=1\napi=2\nmain=does.not.Exist\n").build();
        PluginFailureException e = assertThrows(PluginFailureException.class, () -> manager.install(List.of(jar)));
        assertTrue(e.getMessage().contains("ClassNotFoundException"), e.getMessage());
    }

    // ---------------------------------------------------------------- start / stop / call (R3, R4)

    @Test
    void startAllActivatesEverythingAndV1PluginWorksThroughAdapter() {
        manager.install(List.of(hello("hello", "1"), legacy("legacy", "hello")));
        assertEquals(List.of("hello", "legacy"), manager.startAll());

        assertEquals("Hello, Madiyar!", manager.call("hello", "Madiyar"));
        assertEquals("ABC", manager.call("legacy", "abc"));
        assertTrue(logs.contains("legacy: [api-v1] started through adapter"), logs.toString());
    }

    @Test
    void cannotStartBeforeDependencyIsActive() {
        manager.install(List.of(hello("core", "1"), hello("app", "1", "core")));
        PluginPlatformException e = assertThrows(PluginPlatformException.class, () -> manager.start("app"));
        assertEquals("Cannot start 'app': dependency 'core' is not active", e.getMessage());
        assertSame(PluginState.RESOLVED, manager.state("app"));
    }

    @Test
    void illegalTransitionIsRejectedBeforePluginCodeRuns() {
        manager.install(List.of(hello("a", "1")));
        assertThrows(IllegalTransitionException.class, () -> manager.stop("a"));
        manager.start("a");
        assertThrows(IllegalTransitionException.class, () -> manager.start("a"));
        assertEquals(1, Collections.frequency(logs, "a: started"));
    }

    @Test
    void stopStopsDependantsFirst() {
        manager.install(List.of(hello("core", "1"), hello("app", "1", "core"), hello("other", "1")));
        manager.startAll();
        logs.clear();

        manager.stop("core");

        assertEquals(List.of("app: stopped", "core: stopped"), logs);
        assertEquals(List.of("other"), manager.activePlugins());
        assertSame(PluginState.STOPPED, manager.state("app"));
    }

    @Test
    void stoppedPluginCanBeStartedAgain() {
        manager.install(List.of(hello("a", "1")));
        manager.start("a");
        manager.stop("a");
        manager.start("a");
        assertSame(PluginState.ACTIVE, manager.state("a"));
    }

    @Test
    void callingInactivePluginIsRejected() {
        manager.install(List.of(hello("a", "1")));
        PluginPlatformException e = assertThrows(PluginPlatformException.class, () -> manager.call("a", "x"));
        assertEquals("Plugin 'a' is not active (state RESOLVED)", e.getMessage());
    }

    @Test
    void unknownPluginIsReported() {
        PluginPlatformException e = assertThrows(PluginPlatformException.class, () -> manager.state("ghost"));
        assertEquals("Unknown plugin 'ghost'", e.getMessage());
    }

    @Test
    void startAllRestartsStoppedPluginsInDependencyOrder() {
        manager.install(List.of(hello("core", "1"), hello("app", "1", "core")));
        manager.start("core");
        manager.stop("core");
        // core is STOPPED, app is RESOLVED: startAll must start core first, then app
        assertEquals(List.of("core", "app"), manager.startAll());
    }

    @Test
    void startAllSkipsBrokenPluginAndItsDependants() {
        Path broken = TestPluginJars.jar(dir, "broken.jar")
                .descriptor("broken", "1", 2, BrokenStartPlugin.class).build();
        manager.install(List.of(broken, hello("app", "1", "broken"), hello("fine", "1")));

        assertEquals(List.of("fine"), manager.startAll());

        assertSame(PluginState.RESOLVED, manager.state("broken"), "failed start keeps the old state");
        assertSame(PluginState.RESOLVED, manager.state("app"));
        assertTrue(logs.stream().anyMatch(l -> l.startsWith("broken: not started:") && l.contains("config missing")),
                logs.toString());
        assertTrue(logs.contains("app: not started: Cannot start 'app': dependency 'broken' is not active"),
                logs.toString());
    }

    @Test
    void closeStopsEverythingInReverseOrder() {
        manager.install(List.of(hello("core", "1"), hello("app", "1", "core")));
        manager.startAll();
        logs.clear();
        manager.close();
        assertEquals(List.of("app: stopped", "core: stopped"), logs);
        assertEquals(List.of(), manager.installedPlugins());
    }

    // ---------------------------------------------------------------- uninstall

    @Test
    void uninstallRemovesPlugin() {
        manager.install(List.of(hello("a", "1")));
        manager.start("a");
        manager.uninstall("a");
        assertEquals(List.of(), manager.installedPlugins());
        assertTrue(logs.contains("a: stopped"));
    }

    @Test
    void cannotUninstallPluginOthersDependOn() {
        manager.install(List.of(hello("core", "1"), hello("app", "1", "core")));
        PluginPlatformException e = assertThrows(PluginPlatformException.class, () -> manager.uninstall("core"));
        assertEquals("Cannot uninstall 'core': plugin 'app' depends on it", e.getMessage());
    }

    // ---------------------------------------------------------------- hot reload (R6)

    @Test
    void reloadReplacesActivePluginWithNewVersion() {
        manager.install(List.of(hello("hello", "1.0"), legacy("legacy", "hello")));
        manager.startAll();
        Path v2 = TestPluginJars.jar(dir, "hello-2.jar")
                .descriptor("hello", "2.0", 2, HelloPlugin.class).resource("greeting.txt", "Salem").build();

        manager.reload("hello", v2);

        assertEquals("Salem, Madiyar!", manager.call("hello", "Madiyar"));
        assertEquals("2.0", manager.version("hello"));
        assertSame(PluginState.ACTIVE, manager.state("hello"));
        assertEquals("ABC", manager.call("legacy", "abc"), "other plugins are not affected");
        assertEquals(List.of("hello", "legacy"), manager.installedPlugins(), "load order is kept");
        assertTrue(logs.contains("hello: reloaded: 1.0 -> 2.0"), logs.toString());
    }

    @Test
    void reloadOfStoppedPluginKeepsItInactive() {
        manager.install(List.of(hello("hello", "1.0")));
        manager.reload("hello", hello("hello", "2.0"));
        assertSame(PluginState.RESOLVED, manager.state("hello"));
        assertEquals("2.0", manager.version("hello"));
    }

    @Test
    void reloadWithJarOfAnotherPluginIsRejected() {
        manager.install(List.of(hello("hello", "1.0")));
        PluginPlatformException e = assertThrows(PluginPlatformException.class,
                () -> manager.reload("hello", hello("other", "1.0")));
        assertEquals("Reload of 'hello' rejected: the new JAR is plugin 'other'", e.getMessage());
    }

    @Test
    void failedReloadKeepsOldVersionRunning() {
        manager.install(List.of(hello("hello", "1.0")));
        manager.start("hello");
        Path broken = TestPluginJars.jar(dir, "broken.jar")
                .descriptor("hello", "2.0", 2, LegacyUpperPlugin.class).build();   // wrong type for api=2

        assertThrows(PluginPlatformException.class, () -> manager.reload("hello", broken));

        assertEquals("1.0", manager.version("hello"));
        assertEquals("Hello, x!", manager.call("hello", "x"));
    }

    @Test
    void reloadThatIntroducesCycleIsRejected() {
        manager.install(List.of(hello("a", "1"), hello("b", "1", "a")));
        assertThrows(DependencyCycleException.class, () -> manager.reload("a", hello("a", "2", "b")));
        assertEquals("1", manager.version("a"));
    }
}
