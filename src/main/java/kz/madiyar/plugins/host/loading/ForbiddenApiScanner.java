package kz.madiyar.plugins.host.loading;

import kz.madiyar.plugins.host.PluginFailureException;
import kz.madiyar.plugins.host.PluginPlatformException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * R5: rejects a plugin JAR BEFORE any of its code runs if it calls methods that can kill the host JVM:
 * {@code System.exit}, {@code Runtime.exit}, {@code Runtime.halt}.
 *
 * <p>Why a static check: since Java 18 the SecurityManager is deprecated for removal and since
 * Java 24 it cannot be enabled at all, so there is no reliable runtime way to block System.exit.
 * Every method call in a .class file is listed in its constant pool as a "Methodref"
 * (owner class + method name); this scanner reads the constant pool and looks for those entries.
 *
 * <p>Limitation: a call through reflection ({@code Method.invoke}) is not visible here. See ADR-0003.
 */
public final class ForbiddenApiScanner {

    static final Set<String> FORBIDDEN = Set.of(
            "java/lang/System.exit",
            "java/lang/Runtime.exit",
            "java/lang/Runtime.halt");

    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    public void check(Path jar, String pluginId) {
        List<String> violations = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            for (JarEntry entry : Collections.list(jarFile.entries())) {
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = jarFile.getInputStream(entry)) {
                    for (String call : forbiddenCalls(in.readAllBytes())) {
                        violations.add(entry.getName() + " calls " + call);
                    }
                }
            }
        } catch (IOException e) {
            throw new PluginPlatformException(jar.getFileName() + ": cannot read JAR", e);
        }
        if (!violations.isEmpty()) {
            throw new PluginFailureException(pluginId, "rejected, forbidden API used: " + String.join("; ", violations));
        }
    }

    /** Returns forbidden calls found in one class file, e.g. ["java/lang/System.exit"]. */
    static List<String> forbiddenCalls(byte[] classFile) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(classFile));
        if (in.readInt() != CLASS_FILE_MAGIC) {
            throw new IOException("not a class file");
        }
        in.readUnsignedShort(); // minor version
        in.readUnsignedShort(); // major version

        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        int[] classNameIndex = new int[count];   // CONSTANT_Class      -> index of its name
        int[] refClass = new int[count];         // CONSTANT_Methodref  -> index of owner class
        int[] refNameAndType = new int[count];   // CONSTANT_Methodref  -> index of name-and-type
        int[] natName = new int[count];          // CONSTANT_NameAndType -> index of method name

        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();                              // Utf8
                case 7 -> classNameIndex[i] = in.readUnsignedShort();          // Class
                case 10, 11 -> {                                               // Methodref, InterfaceMethodref
                    refClass[i] = in.readUnsignedShort();
                    refNameAndType[i] = in.readUnsignedShort();
                }
                case 12 -> {                                                   // NameAndType
                    natName[i] = in.readUnsignedShort();
                    in.readUnsignedShort();
                }
                case 8, 16, 19, 20 -> in.readUnsignedShort();                  // String, MethodType, Module, Package
                case 15 -> {                                                   // MethodHandle
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                }
                case 3, 4, 9, 17, 18 -> in.readInt();                          // Integer, Float, Fieldref, Dynamic, InvokeDynamic
                case 5, 6 -> {                                                 // Long, Double take two slots
                    in.readLong();
                    i++;
                }
                default -> throw new IOException("unknown constant pool tag " + tag + " at index " + i);
            }
        }

        List<String> found = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            if (refClass[i] == 0) {
                continue;
            }
            String owner = utf8[classNameIndex[refClass[i]]];
            String method = utf8[natName[refNameAndType[i]]];
            String call = owner + "." + method;
            if (FORBIDDEN.contains(call)) {
                found.add(call);
            }
        }
        return found;
    }
}
