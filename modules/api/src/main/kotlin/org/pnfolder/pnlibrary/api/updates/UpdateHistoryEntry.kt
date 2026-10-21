package org.pnfolder.pnlibrary.api.updates

import java.time.Instant
import java.util.UUID

/**
 * One durable update event, detached from the current resolver plan.
 *
 * @property id unique event identity
 * @property occurredAt UTC event time, not release publication time
 * @property planId related compatibility plan
 * @property state observed outcome; UPDATE_STAGED means prepared, not running
 * @property actor initiator, or UNKNOWN for legacy observations
 * @property operation CHECK, STAGE, ROLLBACK, HEALTH, HEALTH_ROLLBACK or STATE_OBSERVED
 * @property message optional diagnostic detail, never intended as formatted chat markup
 */
class UpdateHistoryEntry(
    val id: UUID,
    val occurredAt: Instant,
    val planId: UUID,
    val state: UpdateState,
    val actor: UpdateActor,
    val operation: String,
    changes: List<ProductChange>,
    val message: String?,
) {
    /** Immutable version transitions associated with this event. */
    val changes: List<ProductChange> = changes.frozen()
}
