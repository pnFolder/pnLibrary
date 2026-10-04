package ru.privatenull.pnlibrary.core.tasks

import ru.privatenull.pnlibrary.api.tasks.TaskQuery
import ru.privatenull.pnlibrary.api.tasks.TaskSnapshot

/** Applies every optional [TaskQuery] criterion to a task snapshot. */
internal object TaskQueryMatcher {
    fun matches(snapshot: TaskSnapshot, query: TaskQuery): Boolean =
        matchesIdentity(snapshot, query) &&
            matchesName(snapshot, query) &&
            matchesState(snapshot, query) &&
            snapshot.tags.containsAll(query.tags)

    private fun matchesIdentity(snapshot: TaskSnapshot, query: TaskQuery): Boolean =
        (query.id == null || snapshot.id == query.id) &&
            (query.key == null || snapshot.key == query.key)

    private fun matchesName(snapshot: TaskSnapshot, query: TaskQuery): Boolean =
        query.nameContains == null ||
            snapshot.name?.contains(query.nameContains!!, ignoreCase = true) == true

    private fun matchesState(snapshot: TaskSnapshot, query: TaskQuery): Boolean =
        (query.statuses.isEmpty() || snapshot.status in query.statuses) &&
            (query.executionKinds.isEmpty() || snapshot.executionKind in query.executionKinds)
}
