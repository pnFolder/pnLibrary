package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs the platform-independent `/pndebug` command flow.
 *
 * Platform adapters remain responsible for permissions, native command
 * registration, and message rendering. Parsing, cooldown, background report
 * creation, and safe reply dispatch are implemented once here.
 *
 * @param library runtime facade used for report generation and task dispatch
 * @param clock monotonic-enough wall clock used by the user-facing cooldown
 */
class DiagnosticCommandExecutor @JvmOverloads constructor(
    private val library: PnLibrary,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lastRequestTimes = ConcurrentHashMap<String, Long>()

    /**
     * Parses and submits one diagnostic command.
     *
     * Immediate events are published on the caller thread. Completion events are
     * dispatched through the public task service to the recipient context.
     */
    fun execute(
        arguments: Array<out String>,
        prefixed: Boolean,
        requesterId: String,
        recipient: Any,
        publish: (DiagnosticCommandEvent) -> Unit,
    ) {
        val request = try {
            DebugRequest.parse(arguments, prefixed)
        } catch (_: IllegalArgumentException) {
            publish(DiagnosticCommandEvent.InvalidUsage)
            return
        }

        val remaining = remainingCooldown(requesterId)
        if (remaining > 0) {
            publish(DiagnosticCommandEvent.CoolingDown(remaining))
            return
        }

        lastRequestTimes[requesterId] = clock.millis()
        publish(DiagnosticCommandEvent.Started(request.target))
        val tasks = library.tasks.scope(library.owner)
        tasks.async(Runnable {
            val event = try {
                DiagnosticCommandEvent.Completed(library.createDiagnosticReport(request))
            } catch (exception: Exception) {
                DiagnosticCommandEvent.Failed(exception.message ?: exception.javaClass.simpleName)
            }
            tasks.entity(recipient, Runnable { publish(event) })
        })
    }

    private fun remainingCooldown(requesterId: String): Long {
        val cooldownMillis = library.configuration.cooldownSeconds * 1_000L
        if (cooldownMillis == 0L) return 0
        val elapsed = clock.millis() - (lastRequestTimes[requesterId] ?: return 0)
        return if (elapsed >= cooldownMillis) 0 else (cooldownMillis - elapsed + 999L) / 1_000L
    }
}

/** State emitted while executing a diagnostic command. */
sealed class DiagnosticCommandEvent {
    /** Command arguments could not be parsed. */
    data object InvalidUsage : DiagnosticCommandEvent()

    /**
     * The requester must wait before another report.
     *
     * @property seconds whole seconds remaining in the cooldown
     */
    data class CoolingDown(val seconds: Long) : DiagnosticCommandEvent()

    /**
     * Report collection has started.
     *
     * @property target normalized plugin target or `all`
     */
    data class Started(val target: String) : DiagnosticCommandEvent()

    /**
     * Report collection completed successfully.
     *
     * @property report generated local or uploaded diagnostic report
     */
    data class Completed(val report: DiagnosticReport) : DiagnosticCommandEvent()

    /**
     * Report collection failed.
     *
     * @property message sanitized detail safe to display to the command sender
     */
    data class Failed(val message: String) : DiagnosticCommandEvent()
}
