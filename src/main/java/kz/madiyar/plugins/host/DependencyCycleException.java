package kz.madiyar.plugins.host;

import java.util.List;

/** Thrown when plugin dependencies form a cycle; the message shows the full path, e.g. a -> b -> c -> a. */
public class DependencyCycleException extends PluginPlatformException {

    private final List<String> cycle;

    public DependencyCycleException(List<String> cycle) {
        super("Dependency cycle detected: " + String.join(" -> ", cycle));
        this.cycle = List.copyOf(cycle);
    }

    public List<String> cycle() {
        return cycle;
    }
}
