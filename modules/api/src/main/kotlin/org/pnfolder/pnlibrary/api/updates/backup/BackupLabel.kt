package org.pnfolder.pnlibrary.api.updates.backup

/** The kind and the display name of one backed-up file. A blank [name] means the library shows the kind. */
data class BackupLabel(val kind: BackupKind, val name: String = "") {
    init {
        require(name.length <= 64 && name.none(Char::isISOControl)) { "Backup name must be at most 64 characters without control characters" }
    }
}
