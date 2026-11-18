package kz.madiyar.plugins.api.v1;

/**
 * Plugin API v1 (old). Existing v1 plugins must keep working without changes,
 * so the host wraps them with an Adapter to {@link kz.madiyar.plugins.api.Plugin}.
 */
public interface LegacyPlugin {

    void init();

    String process(String input);

    void destroy();
}
