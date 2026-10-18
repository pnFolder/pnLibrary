package ru.privatenull.pnlibrary.core.metrics.faststats

import dev.faststats.SimpleContext

/**
 * FastStats session for every platform SDK whose context factory extends [SimpleContext.Factory].
 *
 * Platforms only create their native factory for the project token; the error tracker, the configured
 * metrics and the context lifecycle are attached here.
 *
 * @param nativeFactory creates the platform SDK context factory for a project token
 */
class SimpleFastStatsMetricsSession(
    token: String,
    nativeFactory: (token: String) -> SimpleContext.Factory<*, *>,
) : AbstractFastStatsMetricsSession(
    token = token,
    contextFactory = FastStatsContextFactory { contextToken, tracker, metrics ->
        val context = nativeFactory(contextToken)
            .errorTrackerService(tracker)
            .metrics(metrics)
            .create()
        object : FastStatsContext {
            override fun ready() = context.ready()
            override fun shutdown() = context.shutdown()
        }
    },
)
