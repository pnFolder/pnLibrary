package org.pnfolder.pnlibrary.api.updates.backup

import org.pnfolder.pnlibrary.api.updates.product.ProductId

/**
 * Version information for a retained component backup.
 * @property product managed component identity
 * @property updatedVersion version originally prepared by this transaction, if known
 * @property backupVersion version stored before that update, if known
 */
data class UpdateBackupComponent(val product: ProductId, val updatedVersion: String?, val backupVersion: String?)
