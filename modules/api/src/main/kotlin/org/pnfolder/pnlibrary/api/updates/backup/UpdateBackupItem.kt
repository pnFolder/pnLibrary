package org.pnfolder.pnlibrary.api.updates.backup

import org.pnfolder.pnlibrary.api.updates.product.ProductId

/** One saved file of a backup: what it holds, what it is called and how large it is. */
data class UpdateBackupItem(val product: ProductId, val kind: BackupKind, val name: String, val bytes: Long)
