package kz.madiyar.plugins.host.loading;

import kz.madiyar.plugins.host.PluginDescriptor;
import kz.madiyar.plugins.host.PluginPlatformException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Reads {@code plugin.properties} from the root of a plugin JAR (R1).
 *
 * <pre>
 * id=hello
 * version=1.0.0
 * api=2
 * main=com.example.HelloPlugin
 * dependencies=core,logging      (optional, comma separated)
 * </pre>
 */
public final class DescriptorReader {

    public static final String DESCRIPTOR_FILE = "plugin.properties";

    public PluginDescriptor read(Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            ZipEntry entry = jarFile.getEntry(DESCRIPTOR_FILE);
            if (entry == null) {
                throw new PluginPlatformException(jar.getFileName() + ": " + DESCRIPTOR_FILE + " not found");
            }
            try (InputStream in = jarFile.getInputStream(entry)) {
                return parse(jar, in);
            }
        } catch (IOException e) {
            throw new PluginPlatformException(jar.getFileName() + ": cannot read JAR", e);
        }
    }

    private static PluginDescriptor parse(Path jar, InputStream in) throws IOException {
        Properties props = new Properties();
        props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        return new PluginDescriptor(
                props.getProperty("id"),
                props.getProperty("version"),
                parseApi(jar, props.getProperty("api")),
                props.getProperty("main"),
                parseDependencies(props.getProperty("dependencies", "")));
    }

    private static int parseApi(Path jar, String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new PluginPlatformException(jar.getFileName() + ": 'api' must be 1 or 2, got '" + value + "'");
        }
    }

    private static List<String> parseDependencies(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
