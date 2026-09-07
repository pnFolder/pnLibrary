package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs the platform-independent `/pndebug` command flow.
 *
 * Platform adapters remain responsible for permissions, native command
 * registration, and message rendering. Parsing, cooldown, background report
 * creation, and safe reply dispatch are implemented once here.
 */
class DiagnosticCommandExecutor(private val library: PnLibrary) {
    private val lastRequestTimes = ConcurrentHashMap<String, Long>()

    /**
     * Parses and submits one diagnostic command.
     *
     * Immediate events are published on the caller thread. Completion events are
     * dispatched through [ru.privatenull.pnlibrary.api.platform.PlatformAdapter.executeReply].
     */
    fun execute(
        arguments: Array<out String>,
        prefixed: Boolean,
        requesterId: String,
        recipient: Any,
        publish: (DiagnosticCommandEvent) -> Unit,
    ) {
        val request = runCatching { DebugRequest.parse(arguments, prefixed) }
            .getOrElse {
                publish(DiagnosticCommandEvent.InvalidUsage)
                return
            }

        val remaining = remainingCooldown(requesterId)
        if (remaining > 0) {
            publish(DiagnosticCommandEvent.CoolingDown(remaining))
            return
        }

        lastRequestTimes[requesterId] = System.currentTimeMillis()
        publish(DiagnosticCommandEvent.Started(request.target))
        library.tasks.scope(library.owner).async(Runnable {
            val event = runCatching { library.createDiagnosticReport(request) }.fold(
                onSuccess = { DiagnosticCommandEvent.Completed(it) },
                onFailure = { DiagnosticCommandEvent.Failed(it.message ?: it.javaClass.simpleName) },
            )
            library.platform.executeReply(recipient, Runnable { publish(event) })
        })
    }

    private fun remainingCooldown(requesterId: String): Long {
        val cooldownMillis = library.configuration.cooldownSeconds * 1_000L
        if (cooldownMillis == 0L) return 0
        val elapsed = System.currentTimeMillis() - (lastRequestTimes[requesterId] ?: return 0)
        return if (elapsed >= cooldownMillis) 0 else (cooldownMillis - elapsed + 999L) / 1_000L
    }
}

/** State emitted while executing a diagnostic command. */
sealed class DiagnosticCommandEvent {
    /** Command arguments could not be parsed. */
    data object InvalidUsage : DiagnosticCommandEvent()

    /** The requester must wait [seconds] before another report. */
    data class CoolingDown(val seconds: Long) : DiagnosticCommandEvent()

    /** Report collection has started for [target]. */
    data class Started(val target: String) : DiagnosticCommandEvent()

    /** Report collection completed successfully. */
    data class Completed(val report: DiagnosticReport) : DiagnosticCommandEvent()

    /** Report collection failed with a safe display [message]. */
    data class Failed(val message: String) : DiagnosticCommandEvent()
}
