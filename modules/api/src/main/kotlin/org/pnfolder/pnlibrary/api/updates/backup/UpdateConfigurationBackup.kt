package org.pnfolder.pnlibrary.api.updates.backup

import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections

/**
 * Explicit component-owned files (settings, databases, player data) to preserve before updates.
 * No directory recursion or wildcard selection is performed. Files may contain secrets: the
 * server data directory and its backups must remain private to the server administrator.
 *
 * @property root component data directory, resolved absolutely at declaration time
 */
class UpdateConfigurationBackup(root: Path, files: List<String>, labels: Map<String, BackupLabel>) {
    /** Declares plain configuration files with no labels. */
    constructor(root: Path, files: List<String>) : this(root, files, emptyMap())

    val root: Path = root.toAbsolutePath().normalize()
    /** Immutable normalized paths relative to [root]. Missing files are recorded as absent. */
    val files: List<String> = Collections.unmodifiableList(files.map { value ->
        val path = Paths.get(value).normalize()
        require(value.isNotBlank() && !path.isAbsolute && path.root == null && path.toString().isNotEmpty() && path.toString() != "." &&
            !path.startsWith("..")) {
            "Configuration paths must stay relative to the component data directory"
        }
        require(value.none { it == '*' || it == '?' || it.isISOControl() }) { "Configuration paths must be explicit file names" }
        path.toString()
    })

    /** Kind and name of the files that were labelled, by their normalized path; the others are configuration. */
    val labels: Map<String, BackupLabel> = Collections.unmodifiableMap(labels.mapKeys { Paths.get(it.key).normalize().toString() })

    /** The label of [file], or a configuration label when it has none. */
    fun labelOf(file: String): BackupLabel = labels[file] ?: BackupLabel(BackupKind.CONFIGURATION)

    init {
        require(this.labels.keys.all { it in this.files }) { "A backup label names a file that is not declared" }
        require(this.files.isNotEmpty() && this.files.size <= 100) { "Configuration backup must declare between 1 and 100 files" }
        require(this.files.map { Paths.get(it) }.distinct().size == this.files.size) { "Duplicate configuration paths" }
    }
}
