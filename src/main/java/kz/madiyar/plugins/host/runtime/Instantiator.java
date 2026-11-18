package kz.madiyar.plugins.host.runtime;

import kz.madiyar.plugins.host.PluginPlatformException;

import java.lang.reflect.InvocationTargetException;

/** Creates an instance of a plugin main class through its public no-argument constructor. */
final class Instantiator {

    private Instantiator() {
    }

    static <T> T newInstance(Class<?> mainClass, Class<T> expectedApi, int apiVersion) {
        if (!expectedApi.isAssignableFrom(mainClass)) {
            throw new PluginPlatformException("Main class " + mainClass.getName()
                    + " does not implement " + expectedApi.getName() + " (api=" + apiVersion + ")");
        }
        try {
            return expectedApi.cast(mainClass.getDeclaredConstructor().newInstance());
        } catch (InvocationTargetException e) {
            throw new PluginPlatformException("Constructor of " + mainClass.getName() + " failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new PluginPlatformException(
                    "Cannot create " + mainClass.getName() + ": a public no-argument constructor is required", e);
        }
    }
}
