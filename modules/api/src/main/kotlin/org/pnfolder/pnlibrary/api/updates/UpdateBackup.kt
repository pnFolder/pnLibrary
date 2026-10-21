package org.pnfolder.pnlibrary.api.updates

import java.time.Instant
import java.util.UUID

/**
 * One retained rollback set; paths and backup contents are intentionally not exposed.
 * @property id exact transaction identity required for rollback
 * @property createdAt UTC creation time, or null for legacy backups without a recorded time
 * @property planId originating plan, or null for legacy backups
 */
class UpdateBackup(
    val id: UUID,
    val createdAt: Instant?,
    val planId: UUID?,
    components: List<UpdateBackupComponent>,
    /** Number of validated configuration files retained with this set; zero when unavailable. */
    val configurationFiles: Int = 0,
) {
    /** Immutable description of the complete component set restored together. */
    val components: List<UpdateBackupComponent> = components.frozen()
}

/**
 * Version information for a retained component backup.
 * @property product managed component identity
 * @property updatedVersion version originally prepared by this transaction, if known
 * @property backupVersion version stored before that update, if known
 */
data class UpdateBackupComponent(val product: ProductId, val updatedVersion: String?, val backupVersion: String?)
