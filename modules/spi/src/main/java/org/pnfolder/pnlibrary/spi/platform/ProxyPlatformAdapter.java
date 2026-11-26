package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;

/**
 * Shared lifecycle of the proxy adapters: startup gate, native log observation, replies and shutdown.
 *
 * <p>Startup-dependent callbacks wait for {@link #markReady()}, called by the plugin entry point after runtime creation and
 * native API registration. Proxies have no per-player scheduler, so replies run as global work.
 */
public abstract class ProxyPlatformAdapter implements PlatformAdapter {
    /** Registrations and shutdown state of this adapter. */
    protected final PlatformLifecycle lifecycle = new PlatformLifecycle();
    private final StartupGate startup = new StartupGate(lifecycle, this::executeGlobal);
    private final UncaughtExceptionLogObserver uncaughtExceptionObserver;

    /** @param owner identifies the uncaught-exception observer */
    protected ProxyPlatformAdapter(@NotNull Object owner) {
        this.uncaughtExceptionObserver = new UncaughtExceptionLogObserver(owner);
    }

    @NotNull
    @Override
    public abstract NetworkProxy getNetworkProxy();

    /** Logs a warning through this adapter's log handler. */
    public final void warn(@NotNull String message) {
        getLogHandler().log(this, LogLevel.WARNING, message, null);
    }

    /** Starts or stops forwarding the proxy's own and its plugins' log records to {@code observer}. */
    protected abstract void observeProxyLogs(@Nullable PlatformLogSink observer);

    /** Forwards proxy and plugin warnings/errors and uncaught JVM exceptions, preserving the previous handler. */
    @Override
    public synchronized void observeNativeLogs(@Nullable PlatformLogSink observer) {
        if (observer != null) {
            lifecycle.checkOpen();
        }
        observeProxyLogs(observer);
        uncaughtExceptionObserver.observe(observer);
    }

    /** Defers startup-dependent work until the entry point has published the runtime and native platform API. */
    @Override
    public void whenServerReady(@NotNull Runnable task) {
        startup.whenReady(task);
    }

    /** Releases startup callbacks once; other managed plugins may still use the startup grace. */
    public final void markReady() {
        startup.markReady();
    }

    @Override
    public void executeReply(@NotNull Object recipient, @NotNull Runnable task) {
        executeGlobal(task);
    }

    /** Discards pending startup work, cancels native tasks and stops native log observation once. */
    @Override
    public void close() {
        lifecycle.closeAdapter(startup, getTaskAdapter(), () -> observeNativeLogs(null));
    }
}
