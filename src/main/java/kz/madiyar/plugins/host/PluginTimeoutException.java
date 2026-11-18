package kz.madiyar.plugins.host;

import java.time.Duration;

/** A plugin call did not finish in time. The plugin is quarantined afterwards. */
public class PluginTimeoutException extends PluginFailureException {

    public PluginTimeoutException(String pluginId, String operation, Duration timeout) {
        super(pluginId, operation + " timed out after " + timeout.toMillis() + " ms; plugin quarantined");
    }
}
