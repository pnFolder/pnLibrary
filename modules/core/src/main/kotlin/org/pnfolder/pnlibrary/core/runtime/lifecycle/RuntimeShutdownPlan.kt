package org.pnfolder.pnlibrary.core.runtime.lifecycle

/**
 * The fixed order in which a composed runtime releases its services.
 *
 * Plugins close first, shared services follow, and the provider, platform, and owner hooks run
 * last. Every step runs even when an earlier one fails; failures are rethrown together by
 * [RuntimeShutdown].
 */
internal class RuntimeShutdownPlan(
    private val plugins: AutoCloseable,
    private val commands: AutoCloseable,
    private val audiences: AutoCloseable,
    private val platforms: AutoCloseable,
    private val currency: AutoCloseable,
    private val expansions: AutoCloseable,
    private val libraryExpansions: AutoCloseable,
    private val configurations: AutoCloseable,
    private val libraryMetrics: () -> AutoCloseable?,
    private val metrics: AutoCloseable,
    private val updates: AutoCloseable,
    private val events: AutoCloseable,
    private val services: AutoCloseable,
    private val tasks: AutoCloseable,
    private val support: AutoCloseable,
    private val releaseProvider: () -> Unit,
    private val closePlatform: () -> Unit,
    private val onClose: () -> Unit,
) {
    /** Runs every shutdown step in order. */
    fun run() = RuntimeShutdown().apply {
        add { plugins.close() }
        add { commands.close() }
        add { audiences.close() }
        add { platforms.close() }
        add { currency.close() }
        add { expansions.close() }
        add { libraryExpansions.close() }
        add { configurations.close() }
        add { libraryMetrics()?.close() }
        add { metrics.close() }
        add { updates.close() }
        add { events.close() }
        add { services.close() }
        add { tasks.close() }
        add { support.close() }
        add(releaseProvider)
        add(closePlatform)
        add(onClose)
    }.run()
}
