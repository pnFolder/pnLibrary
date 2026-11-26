package org.pnfolder.pnlibrary.core.metrics.faststats;

import dev.faststats.SimpleContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * FastStats session for every platform SDK whose context factory extends {@link SimpleContext.Factory}.
 *
 * <p>Platforms only create their native factory for the project token; the error tracker, the configured
 * metrics and the context lifecycle are attached here.
 */
public final class SimpleFastStatsMetricsSession extends AbstractFastStatsMetricsSession {

    /**
     * Creates the session.
     *
     * @param token FastStats project token
     * @param nativeFactory creates the platform SDK context factory for a project token
     */
    public SimpleFastStatsMetricsSession(@NotNull String token,
                                         @NotNull Function<String, SimpleContext.Factory<?, ?>> nativeFactory) {
        super(token, contextFactory(nativeFactory));
    }

    private static FastStatsContextFactory contextFactory(Function<String, SimpleContext.Factory<?, ?>> nativeFactory) {
        return (contextToken, tracker, metrics) -> {
            SimpleContext context = nativeFactory.apply(contextToken)
                    .errorTrackerService(tracker)
                    .metrics(metrics)
                    .create();
            return new FastStatsContext() {
                @Override
                public void ready() {
                    context.ready();
                }

                @Override
                public void shutdown() {
                    context.shutdown();
                }
            };
        };
    }
}
