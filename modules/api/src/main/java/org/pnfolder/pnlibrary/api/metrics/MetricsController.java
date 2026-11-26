package org.pnfolder.pnlibrary.api.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runtime control over one plugin's optional metrics session.
 *
 * <p>Provider configurations are retained while metrics are disabled, allowing {@link #enable} to restart the same
 * session. Closing the controller disables the session permanently with its plugin context.
 */
public interface MetricsController extends AutoCloseable {
    /** Whether this controller has been permanently released with its module context. */
    default boolean isClosed() {
        return false;
    }

    /** Whether a metrics session is currently active. */
    boolean isEnabled();

    /** Configured provider backends, including disabled entries retained for status reporting. */
    @NotNull
    default List<MetricsProviderConfiguration> getProviderConfigurations() {
        return Collections.emptyList();
    }

    /** Returns the error reporter of the live session, or null when absent or disabled. */
    @Nullable
    default ErrorReporter errorReporterOrNull() {
        return null;
    }

    /** Starts every enabled configured provider. */
    void enable();

    /** Closes the active metrics session without forgetting its configuration. */
    void disable();

    /**
     * Adds or replaces the configuration of {@code configuration}'s provider, keeping other providers.
     *
     * <p>The session is restarted when metrics are enabled; otherwise the change applies on {@link #enable}.
     */
    void update(@NotNull MetricsProviderConfiguration configuration);

    /** Applies {@code configure} now and again whenever the session is restarted. */
    void configure(@NotNull Consumer<PluginMetrics> configure);

    /** Disables the active session and releases this controller. */
    @Override
    void close();
}
