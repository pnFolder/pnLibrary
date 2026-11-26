package org.pnfolder.pnlibrary.core.metrics.faststats;

import dev.faststats.ErrorTracker;
import dev.faststats.Metrics;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/** Creates the platform-specific FastStats context without leaking SDK types into the API. */
@FunctionalInterface
public interface FastStatsContextFactory {

    /** Builds the native context with its error tracker and lazy metrics factory. */
    @NotNull
    FastStatsContext create(@NotNull String token, @NotNull ErrorTracker tracker,
                            @NotNull Function<Metrics.Factory, Metrics> metrics);
}
