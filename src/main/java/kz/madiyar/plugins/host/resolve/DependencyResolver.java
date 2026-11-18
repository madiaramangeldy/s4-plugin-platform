package kz.madiyar.plugins.host.resolve;

import kz.madiyar.plugins.host.DependencyCycleException;
import kz.madiyar.plugins.host.PluginDescriptor;
import kz.madiyar.plugins.host.PluginPlatformException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * R1: computes the load order of plugins (topological sort).
 * A plugin always comes after all plugins it depends on.
 *
 * <p>Algorithm: depth-first search with three colours.
 * WHITE = not visited, GREY = on the current path, BLACK = finished.
 * Reaching a GREY node again means we walked in a circle -> cycle.
 * Plugins are visited in alphabetical order so the result is deterministic.
 */
public final class DependencyResolver {

    private enum Mark { GREY, BLACK }

    public List<PluginDescriptor> loadOrder(Collection<PluginDescriptor> plugins) {
        Map<String, PluginDescriptor> byId = indexById(plugins);
        checkAllDependenciesExist(byId);

        Map<String, Mark> marks = new HashMap<>();
        List<String> path = new ArrayList<>();
        List<PluginDescriptor> order = new ArrayList<>();
        for (String id : byId.keySet()) {
            visit(id, byId, marks, path, order);
        }
        return List.copyOf(order);
    }

    private static Map<String, PluginDescriptor> indexById(Collection<PluginDescriptor> plugins) {
        Map<String, PluginDescriptor> byId = new TreeMap<>();
        for (PluginDescriptor plugin : plugins) {
            if (byId.put(plugin.id(), plugin) != null) {
                throw new PluginPlatformException("Duplicate plugin id: '" + plugin.id() + "'");
            }
        }
        return byId;
    }

    private static void checkAllDependenciesExist(Map<String, PluginDescriptor> byId) {
        for (PluginDescriptor plugin : byId.values()) {
            for (String dependency : plugin.dependencies()) {
                if (!byId.containsKey(dependency)) {
                    throw new PluginPlatformException(
                            "Plugin '" + plugin.id() + "' depends on missing plugin '" + dependency + "'");
                }
            }
        }
    }

    private static void visit(String id, Map<String, PluginDescriptor> byId, Map<String, Mark> marks,
                              List<String> path, List<PluginDescriptor> order) {
        Mark mark = marks.get(id);
        if (mark == Mark.BLACK) {
            return;
        }
        if (mark == Mark.GREY) {
            throw new DependencyCycleException(cycleEndingAt(id, path));
        }
        marks.put(id, Mark.GREY);
        path.add(id);
        byId.get(id).dependencies().stream()
                .sorted()
                .forEach(dependency -> visit(dependency, byId, marks, path, order));
        path.remove(path.size() - 1);
        marks.put(id, Mark.BLACK);
        order.add(byId.get(id));
    }

    /** path = [x, a, b, c], id = a  ->  [a, b, c, a] */
    private static List<String> cycleEndingAt(String id, List<String> path) {
        List<String> cycle = new ArrayList<>(path.subList(path.indexOf(id), path.size()));
        cycle.add(id);
        return cycle;
    }
}
