package org.pnfolder.pnlibrary.api.updates.backup

import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.frozen
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
    items: List<UpdateBackupItem>,
) {
    /** A backup that does not describe its files one by one. */
    constructor(
        id: UUID,
        createdAt: Instant?,
        planId: UUID?,
        components: List<UpdateBackupComponent>,
        configurationFiles: Int = 0,
    ) : this(id, createdAt, planId, components, configurationFiles, emptyList())

    /** What the saved files are: kind, name and size, so a menu can show an administrator what a copy holds. */
    val items: List<UpdateBackupItem> = items.frozen()

    /** Immutable description of the complete component set restored together. */
    val components: List<UpdateBackupComponent> = components.frozen()
}

/** One saved file of a backup: what it holds, what it is called and how large it is. */
data class UpdateBackupItem(val product: ProductId, val kind: BackupKind, val name: String, val bytes: Long)

/**
 * Version information for a retained component backup.
 * @property product managed component identity
 * @property updatedVersion version originally prepared by this transaction, if known
 * @property backupVersion version stored before that update, if known
 */
data class UpdateBackupComponent(val product: ProductId, val updatedVersion: String?, val backupVersion: String?)
