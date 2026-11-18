package kz.madiyar.plugins.samples;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;

import java.util.function.IntConsumer;

/**
 * Malicious sample: tries to kill the whole host JVM.
 * The platform must reject this JAR at install time, before any of this code runs.
 */
public class ExitPlugin implements Plugin {

    @Override
    public void start(PluginContext context) {
        System.exit(66);
    }

    @Override
    public String handle(String request) {
        if ("halt".equals(request)) {
            Runtime.getRuntime().halt(66);
        }
        IntConsumer exit = System::exit;   // a method reference is caught too
        exit.accept(66);
        return "unreachable";
    }

    @Override
    public void stop() {
        // nothing
    }
}
