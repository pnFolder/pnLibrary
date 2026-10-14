package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot

/** Stable diagnostic representation for one resolved update plan. */
internal object RuntimeUpdateDetails {
    fun of(snapshot: UpdatePlanSnapshot): Map<String, Any?> = linkedMapOf(
        "id" to snapshot.id.toString(),
        "revision" to snapshot.revision,
        "state" to snapshot.state.name,
        "message" to snapshot.message,
        "blockers" to snapshot.blockers.map { it.javaClass.simpleName },
        "targetApi" to snapshot.plan?.targetApi,
        "changeCount" to snapshot.plan?.changes?.size,
        "selectedReleaseCount" to snapshot.plan?.selected?.size,
    )
}
