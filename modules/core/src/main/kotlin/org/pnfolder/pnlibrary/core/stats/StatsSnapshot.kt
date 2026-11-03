package org.pnfolder.pnlibrary.core.stats

/**
 * Aggregated numbers about one moment of the server.
 *
 * Privacy: the snapshot holds counts and plugin ids only. It never holds player names or UUIDs, task names, message
 * texts, addresses or stack traces, so it is safe to keep in the journal and to put in a support archive.
 *
 * @property timeMillis when the numbers were taken, in epoch milliseconds
 * @property tasksActive tasks that are waiting or running now
 * @property tasksFailed tasks that ended with an error, among those still remembered
 * @property taskRuns how many times tasks ran
 * @property taskSkipped how many runs were skipped because a task was still busy or its condition was false
 * @property eventsCalled how many times events were called since the start
 * @property eventsDenied how many of them were cancelled or denied
 * @property warnings24h warnings in the journal over the last 24 hours
 * @property errors24h errors in the journal over the last 24 hours
 * @property errorSources plugins with the most errors over the last 24 hours, worst first
 */
internal data class StatsSnapshot(
    val timeMillis: Long,
    val tasksActive: Int,
    val tasksFailed: Int,
    val taskRuns: Long,
    val taskSkipped: Long,
    val eventsCalled: Long,
    val eventsDenied: Long,
    val warnings24h: Int,
    val errors24h: Int,
    val errorSources: List<Pair<String, Int>>,
) {
    /** The snapshot as journal details; [fromDetails] reads it back. */
    fun toDetails(): Map<String, String> = mapOf(
        "tasksActive" to tasksActive.toString(),
        "tasksFailed" to tasksFailed.toString(),
        "taskRuns" to taskRuns.toString(),
        "taskSkipped" to taskSkipped.toString(),
        "eventsCalled" to eventsCalled.toString(),
        "eventsDenied" to eventsDenied.toString(),
        "warnings24h" to warnings24h.toString(),
        "errors24h" to errors24h.toString(),
        "errorSources" to errorSources.joinToString(",") { (source, count) -> "$source:$count" },
    )

    companion object {
        /** Journal record type of a stored snapshot. */
        const val RECORD_TYPE = "STATS"

        /** Reads a snapshot written by [toDetails], or returns null when a number is missing or broken. */
        fun fromDetails(timeMillis: Long, details: Map<String, String>): StatsSnapshot? {
            fun int(key: String) = details[key]?.toIntOrNull()
            fun long(key: String) = details[key]?.toLongOrNull()
            return StatsSnapshot(
                timeMillis,
                int("tasksActive") ?: return null,
                int("tasksFailed") ?: return null,
                long("taskRuns") ?: return null,
                long("taskSkipped") ?: return null,
                long("eventsCalled") ?: return null,
                long("eventsDenied") ?: return null,
                int("warnings24h") ?: return null,
                int("errors24h") ?: return null,
                details["errorSources"].orEmpty().split(',').filter { it.isNotEmpty() }.mapNotNull { part ->
                    val count = part.substringAfterLast(':').toIntOrNull() ?: return@mapNotNull null
                    part.substringBeforeLast(':') to count
                },
            )
        }
    }
}
