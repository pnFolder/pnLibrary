package ru.privatenull.pnlibrary.api.updates

import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections

/**
 * Explicit component-owned configuration files to preserve before updates.
 * No directory recursion or wildcard selection is performed. Files may contain secrets: the
 * server data directory and its backups must remain private to the server administrator.
 *
 * @property root component data directory, resolved absolutely at declaration time
 */
class UpdateConfigurationBackup(root: Path, files: List<String>) {
    val root: Path = root.toAbsolutePath().normalize()
    /** Immutable normalized paths relative to [root]. Missing files are recorded as absent. */
    val files: List<String> = Collections.unmodifiableList(files.map { value ->
        val path = Paths.get(value).normalize()
        require(value.isNotBlank() && !path.isAbsolute && path.root == null && path.toString().isNotEmpty() && path.toString() != "." && !path.startsWith("..")) {
            "configuration paths must stay relative to the component data directory"
        }
        require(value.none { it == '*' || it == '?' || it.isISOControl() }) { "configuration paths must be explicit file names" }
        path.toString()
    })

    init {
        require(this.files.isNotEmpty() && this.files.size <= 100) { "declare between 1 and 100 configuration files" }
        require(this.files.map { Paths.get(it) }.distinct().size == this.files.size) { "duplicate configuration paths" }
    }
}
