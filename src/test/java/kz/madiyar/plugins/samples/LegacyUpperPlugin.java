package kz.madiyar.plugins.samples;

import kz.madiyar.plugins.api.v1.LegacyPlugin;

/** Sample plugin #2 (old API v1). Must work unchanged through the v1 → v2 Adapter. */
public class LegacyUpperPlugin implements LegacyPlugin {

    private boolean initialized;

    @Override
    public void init() {
        initialized = true;
    }

    @Override
    public String process(String input) {
        if (!initialized) {
            throw new IllegalStateException("init() was not called");
        }
        return input.toUpperCase();
    }

    @Override
    public void destroy() {
        initialized = false;
    }
}
