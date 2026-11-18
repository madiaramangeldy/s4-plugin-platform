package kz.madiyar.plugins.host.resolve;

import kz.madiyar.plugins.host.DependencyCycleException;
import kz.madiyar.plugins.host.PluginDescriptor;
import kz.madiyar.plugins.host.PluginPlatformException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyResolverTest {

    private final DependencyResolver resolver = new DependencyResolver();

    private static PluginDescriptor plugin(String id, String... deps) {
        return new PluginDescriptor(id, "1.0", 2, "x." + id, List.of(deps));
    }

    private static List<String> ids(List<PluginDescriptor> order) {
        return order.stream().map(PluginDescriptor::id).toList();
    }

    @Test
    void emptyInputGivesEmptyOrder() {
        assertEquals(List.of(), resolver.loadOrder(List.of()));
    }

    @Test
    void independentPluginsAreSortedAlphabetically() {
        assertEquals(List.of("a", "b", "c"),
                ids(resolver.loadOrder(List.of(plugin("c"), plugin("a"), plugin("b")))));
    }

    @Test
    void chainIsLoadedDependenciesFirst() {
        // c needs b, b needs a
        List<PluginDescriptor> input = List.of(plugin("c", "b"), plugin("b", "a"), plugin("a"));
        assertEquals(List.of("a", "b", "c"), ids(resolver.loadOrder(input)));
    }

    @Test
    void diamondLoadsSharedDependencyOnce() {
        //      app
        //     /   \
        //   log   db
        //     \   /
        //      core
        List<PluginDescriptor> input = List.of(
                plugin("app", "log", "db"), plugin("log", "core"), plugin("db", "core"), plugin("core"));
        assertEquals(List.of("core", "db", "log", "app"), ids(resolver.loadOrder(input)));
    }

    @Test
    void cycleIsReportedWithFullPath() {
        List<PluginDescriptor> input = List.of(plugin("a", "b"), plugin("b", "c"), plugin("c", "a"));
        DependencyCycleException e = assertThrows(DependencyCycleException.class, () -> resolver.loadOrder(input));
        assertEquals(List.of("a", "b", "c", "a"), e.cycle());
        assertEquals("Dependency cycle detected: a -> b -> c -> a", e.getMessage());
    }

    @Test
    void cycleNotStartingAtFirstPluginShowsOnlyTheCycle() {
        // x -> a -> b -> a : the cycle is a -> b -> a, x is not part of it
        List<PluginDescriptor> input = List.of(plugin("x", "a"), plugin("a", "b"), plugin("b", "a"));
        DependencyCycleException e = assertThrows(DependencyCycleException.class, () -> resolver.loadOrder(input));
        assertEquals(List.of("a", "b", "a"), e.cycle());
    }

    @Test
    void selfDependencyIsACycle() {
        DependencyCycleException e = assertThrows(DependencyCycleException.class, () -> plugin("a", "a"));
        assertEquals(List.of("a", "a"), e.cycle());
    }

    @Test
    void missingDependencyIsReported() {
        PluginPlatformException e = assertThrows(PluginPlatformException.class,
                () -> resolver.loadOrder(List.of(plugin("a", "ghost"))));
        assertEquals("Plugin 'a' depends on missing plugin 'ghost'", e.getMessage());
    }

    @Test
    void duplicateIdIsReported() {
        PluginPlatformException e = assertThrows(PluginPlatformException.class,
                () -> resolver.loadOrder(List.of(plugin("a"), plugin("a"))));
        assertEquals("Duplicate plugin id: 'a'", e.getMessage());
    }

    /** Property: for random acyclic graphs every dependency is loaded before its dependant. */
    @Test
    void randomAcyclicGraphsAlwaysRespectDependencies() {
        long seed = 42L;
        Random random = new Random(seed);
        for (int round = 0; round < 500; round++) {
            int n = 1 + random.nextInt(30);
            List<PluginDescriptor> input = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                List<String> deps = new java.util.ArrayList<>();
                for (int j = 0; j < i; j++) {          // only edges to smaller index -> no cycles
                    if (random.nextInt(4) == 0) {
                        deps.add("p" + j);
                    }
                }
                input.add(plugin("p" + i, deps.toArray(String[]::new)));
            }
            java.util.Collections.shuffle(input, random);

            List<String> order = ids(resolver.loadOrder(input));
            assertEquals(n, order.size(), "seed=" + seed + " round=" + round);
            for (PluginDescriptor p : input) {
                for (String dep : p.dependencies()) {
                    assertTrue(order.indexOf(dep) < order.indexOf(p.id()),
                            "seed=" + seed + " round=" + round + ": " + dep + " must come before " + p.id());
                }
            }
        }
    }
}
