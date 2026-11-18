package kz.madiyar.plugins.samples;

import kz.madiyar.plugins.api.Plugin;
import kz.madiyar.plugins.api.PluginContext;

/**
 * Sample plugin #3 — malicious on purpose. Every request tries to hurt the host:
 * <ul>
 *   <li>{@code loop} — infinite loop (never returns);</li>
 *   <li>{@code boom} — throws a RuntimeException;</li>
 *   <li>{@code oom}  — OutOfMemoryError (asks for an array larger than the VM allows);</li>
 *   <li>{@code exit} — calls System.exit to kill the whole JVM.</li>
 * </ul>
 */
public class EvilPlugin implements Plugin {

    static volatile long sink;

    @Override
    public void start(PluginContext context) {
        context.log("evil plugin started");
    }

    @Override
    public String handle(String request) {
        switch (request) {
            case "loop" -> {
                long i = 0;
                while (true) {
                    sink = i++;
                }
            }
            case "boom" -> throw new IllegalStateException("evil plugin exploded");
            case "oom" -> {
                long[] huge = new long[Integer.MAX_VALUE];
                sink = huge.length;
                return "unreachable";
            }
            case "exit" -> {
                System.exit(66);
                return "unreachable";
            }
            default -> {
                return "evil says: " + request;
            }
        }
    }

    @Override
    public void stop() {
        // nothing to release
    }
}
