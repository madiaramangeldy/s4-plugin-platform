package kz.madiyar.plugins.testsupport;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;

/** Test-only plugin whose start() always fails. */
public class BrokenStartPlugin implements Plugin {

    @Override
    public void start(PluginContext context) {
        throw new IllegalStateException("cannot start: config missing");
    }

    @Override
    public String handle(String request) {
        return request;
    }

    @Override
    public void stop() {
        // nothing
    }
}
