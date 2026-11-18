package kz.madiyar.plugins.host;

import java.util.List;
import java.util.Objects;

/**
 * Metadata of a plugin: read from {@code plugin.properties} inside the JAR (R1).
 *
 * @param id           unique plugin name
 * @param version      plugin version, e.g. "1.0.0"
 * @param apiVersion   which plugin API it implements: 1 or 2
 * @param mainClass    fully qualified name of the class implementing the API
 * @param dependencies ids of plugins that must be loaded before this one
 */
public record PluginDescriptor(String id, String version, int apiVersion,
                               String mainClass, List<String> dependencies) {

    public PluginDescriptor {
        requireText(id, "id");
        requireText(version, "version");
        requireText(mainClass, "mainClass");
        if (apiVersion != 1 && apiVersion != 2) {
            throw new PluginPlatformException(
                    "Plugin '" + id + "': unsupported api version " + apiVersion + " (expected 1 or 2)");
        }
        Objects.requireNonNull(dependencies, "dependencies");
        dependencies = List.copyOf(dependencies);
        if (dependencies.contains(id)) {
            throw new DependencyCycleException(List.of(id, id));
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PluginPlatformException("Plugin descriptor: '" + field + "' must not be empty");
        }
    }
}
