package kz.madiyar.plugins.host.barrier;

import kz.madiyar.plugins.host.PluginFailureException;
import kz.madiyar.plugins.host.PluginTimeoutException;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * R5: runs plugin code on the plugin's own thread, never on the host thread.
 *
 * <ul>
 *   <li>Any {@link Throwable} from the plugin (RuntimeException, OutOfMemoryError,
 *       StackOverflowError, LinkageError...) is turned into a {@link PluginFailureException}.</li>
 *   <li>A call that does not finish within the timeout is abandoned: the host gets a
 *       {@link PluginTimeoutException} and the plugin is quarantined (all later calls are rejected).</li>
 * </ul>
 *
 * <p>Limitation: Java cannot kill a thread. A plugin that loops forever and ignores
 * interruption keeps its (daemon) thread busy; the host is not blocked, but that thread
 * and the plugin's classes stay in memory until the JVM exits. See ADR-0003.
 */
public final class FaultBarrier implements AutoCloseable {

    private final String pluginId;
    private final Duration timeout;
    private final ExecutorService executor;
    private volatile boolean quarantined;

    public FaultBarrier(String pluginId, Duration timeout, ClassLoader pluginLoader) {
        this.pluginId = pluginId;
        this.timeout = timeout;
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
            Thread thread = new Thread(task, "plugin-" + pluginId);
            thread.setDaemon(true);                    // a stuck plugin never keeps the JVM alive
            thread.setContextClassLoader(pluginLoader);
            return thread;
        });
    }

    public <T> T call(String operation, Callable<T> action) {
        if (quarantined) {
            throw new PluginFailureException(pluginId, operation + " rejected: plugin is quarantined after a timeout");
        }
        Future<T> future;
        try {
            future = executor.submit(action);
        } catch (RejectedExecutionException e) {
            throw new PluginFailureException(pluginId, operation + " rejected: plugin runtime is closed", e);
        }
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            quarantined = true;
            future.cancel(true);
            executor.shutdownNow();
            throw new PluginTimeoutException(pluginId, operation, timeout);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new PluginFailureException(pluginId, operation + " failed: " + cause, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PluginFailureException(pluginId, operation + " interrupted", e);
        }
    }

    public boolean isQuarantined() {
        return quarantined;
    }

    /** Stops the plugin thread. Waits a little so that an idle thread is really gone (needed for GC in R6). */
    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
