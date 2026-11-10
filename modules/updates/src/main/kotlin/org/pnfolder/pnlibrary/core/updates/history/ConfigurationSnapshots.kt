package org.pnfolder.pnlibrary.core.updates.history

import com.google.gson.Gson
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind
import org.pnfolder.pnlibrary.api.updates.backup.BackupLabel
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.io.TextFiles
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.updates.engine.UpdatePaths
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Explicit, bounded configuration snapshots. Capturing never mutates live configuration files. */
internal class ConfigurationSnapshots(
    private val directory: Path,
    private val pluginsDirectory: Path,
    private val maximumFileBytes: Long = 4L * 1024 * 1024,
    private val maximumTotalBytes: Long = 16L * 1024 * 1024,
    private val maximumDataFileBytes: Long = 1024L * 1024 * 1024,
    private val maximumDataTotalBytes: Long = 4L * 1024 * 1024 * 1024,
) {
    /** One file of a snapshot: where it lives, and the saved copy ([blob]) with its size and checksum. */
    data class FileRecord(val product: String, val root: String, val relative: String, val existed: Boolean,
        val blob: String?, val size: Long, val sha256: String?, val kind: String? = null, val name: String? = null) {
        /** What the file holds; snapshots from older versions held configuration only. */
        val backupKind: BackupKind get() = kind?.let(BackupKind::valueOf) ?: BackupKind.CONFIGURATION
    }
    /** The manifest of a snapshot: the plan it was taken for and its files. */
    data class Snapshot(val schema: Int, val id: String, val planId: String, val createdAt: String, val files: List<FileRecord>)
    private val gson = Gson()

    init { require(maximumFileBytes > 0 && maximumTotalBytes > 0 && maximumDataFileBytes > 0 && maximumDataTotalBytes > 0) }

    private fun fileLimit(kind: BackupKind) = if (kind == BackupKind.CONFIGURATION) maximumFileBytes else maximumDataFileBytes

    /** Saves the selected files of [selections] for [planId]; returns the snapshot id, or null when nothing is selected. */
    fun capture(planId: UUID, selections: Map<ProductId, UpdateConfigurationBackup>): UUID? {
        if (selections.isEmpty()) return null
        require(selections.values.sumOf { it.files.size } <= 100) { "Configuration snapshot exceeds 100 files" }
        val sources = selections.flatMap { (product, selection) ->
            selection.files.map { relative -> Source(product, selection, relative, checkedSource(selection.root, relative)) }
        }
        require(sources.map { it.path }.distinct().size == sources.size) { "Configuration files overlap across components" }
        val id = UUID.randomUUID()
        val target = directory.toAbsolutePath().normalize().resolve(id.toString())
        UpdatePaths.inside(target.resolve("manifest.json"), directory)
        Files.createDirectories(directory)
        createPrivateDirectory(target)
        try {
            val snapshot = Snapshot(1, id.toString(), planId.toString(), Instant.now().toString(), copyAll(sources, target))
            writeManifest(target, snapshot)
        } catch (error: Throwable) {
            runCatchingNonFatal { BackupStorage.delete(target) }.onFailure(error::addSuppressed)
            throw error
        }
        return id
    }

    private class Source(val product: ProductId, val request: UpdateConfigurationBackup, val relative: String, val path: Path)

    private fun createPrivateDirectory(target: Path) {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createDirectory(target, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        } else {
            Files.createDirectory(target)
        }
    }

    private fun copyAll(sources: List<Source>, target: Path): List<FileRecord> {
        var configurationTotal = 0L
        var dataTotal = 0L
        return sources.mapIndexed { index, source ->
            val label = source.request.labelOf(source.relative)
            if (!Files.exists(source.path, LinkOption.NOFOLLOW_LINKS)) {
                return@mapIndexed FileRecord(
                    source.product.value, source.request.root.toString(), source.relative, false, null, 0, null,
                    label.kind.name, label.name,
                )
            }
            val isConfiguration = label.kind == BackupKind.CONFIGURATION
            val room = if (isConfiguration) maximumTotalBytes - configurationTotal else maximumDataTotalBytes - dataTotal
            val blob = index.toString().padStart(4, '0') + ".bin"
            val record = copyFile(source, label, blob, target, minOf(fileLimit(label.kind), room))
            if (isConfiguration) configurationTotal += record.size else dataTotal += record.size
            record
        }
    }

    /** Copies one existing file and fails when it changed while it was being read. */
    private fun copyFile(source: Source, label: BackupLabel, blob: String, target: Path, limit: Long): FileRecord {
        val before = Files.readAttributes(source.path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        require(before.isRegularFile) { "Configuration selection must name a regular file" }
        require(before.size() <= limit) { "Backup of ${source.relative} exceeds the size limit" }
        val (size, hash) = copyBounded(source.path, target.resolve(blob), limit)
        val after = Files.readAttributes(source.path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        require(before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime() && before.fileKey() == after.fileKey()) {
            "Configuration changed while being captured"
        }
        return FileRecord(
            source.product.value, source.request.root.toString(), source.relative, true, blob, size, hash,
            label.kind.name, label.name,
        )
    }

    private fun writeManifest(target: Path, snapshot: Snapshot) {
        val temporary = target.resolve("manifest.tmp")
        TextFiles.write(temporary, gson.toJson(snapshot), UTF_8)
        FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
        AtomicFiles.move(temporary, target.resolve("manifest.json"), replaceExisting = false)
    }

    /** Validates the complete manifest and every backup byte before any restoration may begin. */
    fun read(id: UUID): Snapshot {
        val folder = directory.toAbsolutePath().normalize().resolve(id.toString())
        val manifest = UpdatePaths.inside(folder.resolve("manifest.json"), directory)
        require(Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) && Files.size(manifest) <= 1024 * 1024) { "Invalid configuration manifest" }
        val snapshot = gson.fromJson(TextFiles.read(manifest, UTF_8), Snapshot::class.java)
        require(snapshot.schema == 1 && snapshot.id == id.toString()) { "Configuration snapshot identity mismatch" }
        UUID.fromString(snapshot.planId)
        Instant.parse(snapshot.createdAt)
        require(snapshot.files.isNotEmpty() && snapshot.files.size <= 100) { "Invalid configuration file count" }
        val sources = mutableSetOf<Path>()
        val blobs = mutableSetOf<String>()
        var configurationTotal = 0L
        var dataTotal = 0L
        snapshot.files.forEach { file ->
            ProductId.of(file.product)
            val declaration = UpdateConfigurationBackup(Paths.get(file.root), listOf(file.relative))
            require(sources.add(checkedSource(declaration.root, declaration.files.single()))) { "Duplicate configuration target" }
            val kind = file.backupKind
            require(file.size in 0..fileLimit(kind)) { "Invalid backup size" }
            if (kind == BackupKind.CONFIGURATION) configurationTotal += file.size else dataTotal += file.size
            require(configurationTotal <= maximumTotalBytes && dataTotal <= maximumDataTotalBytes) { "Backup snapshot exceeds size limit" }
            if (file.existed) {
                require(file.blob != null && file.blob.matches(Regex("[0-9]{4}\\.bin")) && blobs.add(file.blob)) { "Invalid configuration blob" }
                val blob = UpdatePaths.inside(folder.resolve(file.blob), folder)
                require(Files.isRegularFile(blob, LinkOption.NOFOLLOW_LINKS) && Files.size(blob) == file.size) { "Configuration backup size mismatch" }
                require(hash(blob, fileLimit(kind)) == file.sha256) { "Configuration backup checksum mismatch" }
            } else require(file.blob == null && file.sha256 == null && file.size == 0L) { "Invalid absent configuration record" }
        }
        return snapshot
    }

    private fun checkedSource(root: Path, relative: String): Path {
        UpdatePaths.inside(root, pluginsDirectory)
        val source = UpdatePaths.inside(root.resolve(relative), root)
        return UpdatePaths.inside(source, pluginsDirectory)
    }

    /** The live path a [file] record would be restored to, checked to stay inside the plugins folder. */
    fun target(file: FileRecord): Path = checkedSource(Paths.get(file.root), file.relative)

    /** Copies a validated blob to a temporary file, checking its bytes again during the copy. */
    fun copyForRestore(id: UUID, file: FileRecord, temporary: Path) {
        require(file.existed && file.blob != null) { "Configuration record has no backup content" }
        val folder = directory.toAbsolutePath().normalize().resolve(id.toString())
        val blob = UpdatePaths.inside(folder.resolve(file.blob), folder)
        val (size, digest) = copyBounded(blob, temporary, file.size, replaceExisting = true)
        require(size == file.size && digest == file.sha256) { "Configuration backup changed during restoration" }
    }

    private fun copyBounded(source: Path, target: Path, maximum: Long, replaceExisting: Boolean = false): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { input ->
            val options = if (replaceExisting) arrayOf<OpenOption>(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)
                else arrayOf<OpenOption>(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            Files.newOutputStream(target, *options).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    require(count <= maximum) { "Configuration snapshot exceeds size limit" }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        FileChannel.open(target, StandardOpenOption.WRITE).use { it.force(true) }
        return count to hex(digest.digest())
    }

    private fun hash(path: Path, limit: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= limit) { "Configuration backup exceeds size limit" }
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
