package org.pnfolder.pnlibrary.core.updates.history

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant

/** Disk space of saved rollback sets and their removal. */
internal object BackupStorage {
    /** Rollback sets older than this are removed on their own. */
    val RETENTION: Duration = Duration.ofDays(30)

    /** A set this large is reported to the administrator, who can delete it. */
    const val LARGE_BYTES: Long = 500L * 1024 * 1024

    /** Total size of the files under [folder]; 0 when it does not exist. */
    fun bytes(folder: Path): Long {
        if (!Files.isDirectory(folder)) return 0
        return Files.walk(folder).use { paths -> paths.filter(Files::isRegularFile).mapToLong(Files::size).sum() }
    }

    /** True when a set created at [createdAt] has outlived [RETENTION]; a set with no date is kept. */
    fun expired(createdAt: Instant?, now: Instant): Boolean = createdAt != null && createdAt.plus(RETENTION).isBefore(now)

    /** Removes [folder] with everything in it; does nothing when it does not exist. */
    fun delete(folder: Path) {
        if (!Files.isDirectory(folder)) return
        Files.walk(folder).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    /** The folder of [name] inside [parent], whatever its letter case, or null when there is none. */
    fun child(parent: Path, name: String): Path? {
        if (!Files.isDirectory(parent)) return null
        return Files.list(parent).use { paths ->
            paths.filter { Files.isDirectory(it) && it.fileName.toString().equals(name, ignoreCase = true) }.findFirst().orElse(null)
        }
    }

    /** Copies every file under [from] into [to], keeping the folder structure. */
    fun copy(from: Path, to: Path) {
        Files.walk(from).use { paths ->
            paths.forEach { path ->
                val target = to.resolve(from.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}
