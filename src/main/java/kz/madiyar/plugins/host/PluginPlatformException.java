package kz.madiyar.plugins.host;

/** Base class for every error the platform reports. */
public class PluginPlatformException extends RuntimeException {

    public PluginPlatformException(String message) {
        super(message);
    }

    public PluginPlatformException(String message, Throwable cause) {
        super(message, cause);
    }
}
