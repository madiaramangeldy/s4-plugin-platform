package kz.madiyar.plugins.samples;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Sample plugin #1 (API v2). Greets the caller.
 * The greeting text is read from {@code greeting.txt} inside its own JAR,
 * so two JAR versions of the same class can behave differently (used by hot reload tests).
 */
public class HelloPlugin implements Plugin {

    private PluginContext context;
    private String greeting = "Hello";

    @Override
    public void start(PluginContext context) {
        this.context = context;
        try (InputStream in = HelloPlugin.class.getResourceAsStream("/greeting.txt")) {
            if (in != null) {
                greeting = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            context.log("cannot read greeting.txt, using default");
        }
        context.log("started");
    }

    @Override
    public String handle(String request) {
        return greeting + ", " + request + "!";
    }

    @Override
    public void stop() {
        context.log("stopped");
    }
}
