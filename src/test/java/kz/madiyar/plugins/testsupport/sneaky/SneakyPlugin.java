package kz.madiyar.plugins.testsupport.sneaky;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;
import kz.madiyar.plugins.host.resolve.DependencyResolver;

import java.util.List;

/**
 * Test-only plugin that BREAKS the R2 rule on purpose: it uses a host-internal class.
 * Used to prove that (1) the ClassLoader blocks it at runtime and
 * (2) the ArchUnit rule really catches such code (the rule is not "weak").
 * Lives outside the samples package so the real samples stay clean.
 */
public class SneakyPlugin implements Plugin {

    @Override
    public void start(PluginContext context) {
        // nothing
    }

    @Override
    public String handle(String request) {
        return "resolved " + new DependencyResolver().loadOrder(List.of()).size();
    }

    @Override
    public void stop() {
        // nothing
    }
}
