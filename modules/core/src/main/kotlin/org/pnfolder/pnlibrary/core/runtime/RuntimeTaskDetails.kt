package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot

/** Stable diagnostic representation for the runtime task registry. */
internal object RuntimeTaskDetails {
    fun entries(snapshots: List<TaskSnapshot>): List<Map<String, Any?>> =
        snapshots.map { task ->
            linkedMapOf(
                "id" to task.id.value,
                "name" to task.name,
                "owner" to task.ownerName,
                "execution" to task.executionKind.name,
                "status" to task.status.name,
                "runCount" to task.runCount,
                "skippedCount" to task.skippedCount,
                "lastFailure" to task.lastFailure,
            )
        }

    fun summary(snapshots: List<TaskSnapshot>): Map<String, Any?> = linkedMapOf(
        "count" to snapshots.size,
        "byStatus" to snapshots.groupingBy { it.status.name }.eachCount(),
        "byExecution" to snapshots.groupingBy { it.executionKind.name }.eachCount(),
        "runCount" to snapshots.sumOf { it.runCount },
        "skippedCount" to snapshots.sumOf { it.skippedCount },
        "failedCount" to snapshots.count { it.lastFailure != null },
        "failuresByOwner" to snapshots
            .filter { it.lastFailure != null }
            .groupingBy { it.ownerName }
            .eachCount(),
        "failureMessages" to snapshots
            .mapNotNull { it.lastFailure?.take(256) }
            .groupingBy { it }
            .eachCount(),
    )
}
