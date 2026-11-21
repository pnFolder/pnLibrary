package org.pnfolder.pnlibrary.api.updates.backup

import java.nio.file.Path

/**
 * A copy the plugin makes itself, for data the library cannot copy as a file, such as a MySQL database.
 * Before an update the library calls [export] with the file to write; the file is kept with the backup set
 * and is NOT restored by a rollback: the administrator loads it back by hand.
 * @property fileName name of the exported file inside the set, without folders
 */
class BackupExport(val kind: BackupKind, val name: String, val fileName: String, val export: (Path) -> Unit) {
    init {
        BackupLabel(kind, name)
        require(fileName.isNotBlank() && fileName.none { it == '/' || it == '\\' || it.isISOControl() } && fileName != "." && fileName != "..") {
            "Export file name must be a plain file name"
        }
    }
}
