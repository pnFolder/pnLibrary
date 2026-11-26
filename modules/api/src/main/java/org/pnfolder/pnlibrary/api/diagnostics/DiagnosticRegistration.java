package org.pnfolder.pnlibrary.api.diagnostics;

/**
 * A handle returned by {@link DiagnosticsService#register}. Closing it removes the contributor from the registry: call
 * this from your plugin's {@code onDisable} to avoid stale contributors on hot-reload.
 */
@FunctionalInterface
public interface DiagnosticRegistration extends AutoCloseable {
    /** Whether this contributor has already been removed. */
    default boolean isClosed() {
        return false;
    }

    /** Removes the registered contributor; repeated calls are safe. */
    @Override
    void close();
}
