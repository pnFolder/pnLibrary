package org.pnfolder.pnlibrary.core.updates.history

import com.google.gson.Gson
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.core.io.TextFiles
import org.pnfolder.pnlibrary.core.updates.engine.UpdatePaths
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import kotlin.streams.toList

/** Offline-only restoration engine. Runtime commands must queue work, never call this on live files. */
internal class ConfigurationRestorer(private val snapshots: ConfigurationSnapshots, private val directory: Path) {
    data class Journal(val id: String, val source: String, val before: String, var state: String, val operation: String? = null)
    private val gson = Gson()

    /** Preserves current files first, then replaces only paths explicitly recorded in the snapshot. */
    fun restore(source: UUID, operation: UUID? = null): UUID {
        val snapshot = snapshots.read(source)
        if (operation != null) {
            val previous = journals().map { it to readJournal(it) }.filter { it.second.operation == operation.toString() }
            require(previous.all { it.second.source == source.toString() }) { "Restore operation was bound to another snapshot" }
            previous.firstOrNull { it.second.state == "APPLIED" }?.let { return UUID.fromString(it.second.id) }
        }
        val declarations = snapshot.files.groupBy { it.product }.map { (product, files) ->
            require(files.map { it.root }.distinct().size == 1) { "Component has conflicting configuration roots" }
            ProductId.of(product) to UpdateConfigurationBackup(Paths.get(files.first().root), files.map { it.relative })
        }.toMap()
        val before = requireNotNull(snapshots.capture(UUID.fromString(snapshot.planId), declarations))
        val id = UUID.randomUUID()
        val file = UpdatePaths.inside(directory.resolve(id.toString()).resolve("restore.json"), directory)
        val journal = Journal(id.toString(), source.toString(), before.toString(), "PREPARED", operation?.toString())
        save(file, journal)
        try {
            journal.state = "APPLYING"
            save(file, journal)
            apply(source)
            journal.state = "APPLIED"
            save(file, journal)
        } catch (error: Throwable) {
            runCatching { recover(file, journal) }.onFailure(error::addSuppressed)
            throw error
        }
        return id
    }

    /** Rolls back interrupted restores to the pre-restore snapshot before accepting more work. */
    fun recoverInterrupted() {
        journals().forEach { file ->
            val journal = readJournal(file)
            if (journal.state in setOf("APPLYING", "RECOVERING")) recover(file, journal)
        }
    }

    /** Reverts a completed configuration restore if its enclosing JAR installation cannot finish. */
    fun undo(id: UUID) {
        val file = directory.resolve(id.toString()).resolve("restore.json")
        val journal = readJournal(file)
        if (journal.state == "RECOVERED") return
        require(journal.state in setOf("APPLIED", "APPLYING", "RECOVERING")) { "Configuration restore has not been applied" }
        recover(file, journal)
    }

    private fun journals(): List<Path> {
        if (!Files.exists(directory)) return emptyList()
        return Files.list(directory).use { folders ->
            folders.filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }
                .map { UpdatePaths.inside(it.resolve("restore.json"), directory) }
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.toList()
        }
    }

    private fun readJournal(path: Path): Journal {
        val file = UpdatePaths.inside(path, directory)
        require(Files.size(file) <= 64 * 1024) { "Restore journal exceeds size limit" }
        return gson.fromJson(TextFiles.read(file, UTF_8), Journal::class.java).also { journal ->
            require(UUID.fromString(journal.id).toString() == file.parent.fileName.toString()) { "Restore identity mismatch" }
            UUID.fromString(journal.source)
            UUID.fromString(journal.before)
            journal.operation?.let(UUID::fromString)
            require(journal.state in setOf("PREPARED", "APPLYING", "RECOVERING", "APPLIED", "RECOVERED")) { "Invalid restore state" }
        }
    }

    private fun recover(file: Path, journal: Journal) {
        journal.state = "RECOVERING"
        save(file, journal)
        apply(UUID.fromString(journal.before))
        journal.state = "RECOVERED"
        save(file, journal)
    }

    private fun apply(id: UUID) {
        val snapshot = snapshots.read(id)
        // Every record is validated above before the first target is modified.
        snapshot.files.forEach { record ->
            val target = snapshots.target(record)
            require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                "Configuration target is not a regular file"
            }
        }
        snapshot.files.forEach { record ->
            val target = snapshots.target(record)
            if (!record.existed) {
                require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(target,
                    LinkOption.NOFOLLOW_LINKS)) { "Configuration target is not a regular file" }
                Files.deleteIfExists(target)
            } else {
                Files.createDirectories(target.parent)
                val attributes = if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
                    arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) else emptyArray()
                val temporary = Files.createTempFile(target.parent, ".pnlibrary-restore-", ".tmp", *attributes)
                try {
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        val sourceAcl = Files.getFileAttributeView(target, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
                        val destinationAcl = Files.getFileAttributeView(temporary, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
                        if (sourceAcl != null && destinationAcl != null) destinationAcl.acl = sourceAcl.acl
                    }
                    snapshots.copyForRestore(id, record, temporary)
                    // Recheck confinement immediately before publication.
                    snapshots.target(record)
                    AtomicFiles.move(temporary, target)
                } finally { Files.deleteIfExists(temporary) }
            }
        }
    }

    private fun save(file: Path, journal: Journal) {
        UpdatePaths.inside(file, directory)
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, ".restore-", ".tmp")
        try {
            TextFiles.write(temporary, gson.toJson(journal), UTF_8)
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            AtomicFiles.move(temporary, file)
        } finally { Files.deleteIfExists(temporary) }
    }
}
