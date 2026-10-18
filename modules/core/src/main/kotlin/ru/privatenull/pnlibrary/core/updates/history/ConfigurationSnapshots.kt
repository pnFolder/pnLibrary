package ru.privatenull.pnlibrary.core.updates.history

import com.google.gson.Gson
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.UpdateConfigurationBackup
import ru.privatenull.pnlibrary.common.io.AtomicFiles
import ru.privatenull.pnlibrary.core.io.TextFiles
import ru.privatenull.pnlibrary.core.updates.engine.UpdatePaths
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
) {
    data class FileRecord(val product: String, val root: String, val relative: String, val existed: Boolean,
        val blob: String?, val size: Long, val sha256: String?)
    data class Snapshot(val schema: Int, val id: String, val planId: String, val createdAt: String, val files: List<FileRecord>)
    private val gson = Gson()

    init { require(maximumFileBytes > 0 && maximumTotalBytes > 0) }

    fun capture(planId: UUID, selections: Map<ProductId, UpdateConfigurationBackup>): UUID? {
        if (selections.isEmpty()) return null
        require(selections.values.sumOf { it.files.size } <= 100) { "configuration snapshot exceeds 100 files" }
        val sources = selections.flatMap { (product, selection) -> selection.files.map { relative ->
            Triple(product, selection, relative) to checkedSource(selection.root, relative)
        } }
        require(sources.map { it.second }.distinct().size == sources.size) { "configuration files overlap across components" }
        val id = UUID.randomUUID()
        val target = directory.toAbsolutePath().normalize().resolve(id.toString())
        UpdatePaths.inside(target.resolve("manifest.json"), directory)
        Files.createDirectories(directory)
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createDirectory(target, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        } else Files.createDirectory(target)
        var total = 0L
        val records = sources.mapIndexed { index, (selection, source) ->
            val (product, request, relative) = selection
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                FileRecord(product.value, request.root.toString(), relative, false, null, 0, null)
            } else {
                val before = Files.readAttributes(source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                require(before.isRegularFile) { "configuration selection must name a regular file" }
                val limit = minOf(maximumFileBytes, maximumTotalBytes - total)
                require(before.size() <= limit) { "configuration snapshot exceeds size limit" }
                val blob = index.toString().padStart(4, '0') + ".bin"
                val (size, hash) = copyBounded(source, target.resolve(blob), limit)
                val after = Files.readAttributes(source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                require(before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime() && before.fileKey() == after.fileKey()) {
                    "configuration changed while being captured"
                }
                total += size
                FileRecord(product.value, request.root.toString(), relative, true, blob, size, hash)
            }
        }
        val snapshot = Snapshot(1, id.toString(), planId.toString(), Instant.now().toString(), records)
        val temporary = target.resolve("manifest.tmp")
        TextFiles.write(temporary, gson.toJson(snapshot), UTF_8)
        FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
        AtomicFiles.move(temporary, target.resolve("manifest.json"), replaceExisting = false)
        return id
    }

    /** Validates the complete manifest and every backup byte before any restoration may begin. */
    fun read(id: UUID): Snapshot {
        val folder = directory.toAbsolutePath().normalize().resolve(id.toString())
        val manifest = UpdatePaths.inside(folder.resolve("manifest.json"), directory)
        require(Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) && Files.size(manifest) <= 1024 * 1024) { "invalid configuration manifest" }
        val snapshot = gson.fromJson(TextFiles.read(manifest, UTF_8), Snapshot::class.java)
        require(snapshot.schema == 1 && snapshot.id == id.toString()) { "configuration snapshot identity mismatch" }
        UUID.fromString(snapshot.planId)
        Instant.parse(snapshot.createdAt)
        require(snapshot.files.isNotEmpty() && snapshot.files.size <= 100) { "invalid configuration file count" }
        val sources = mutableSetOf<Path>()
        val blobs = mutableSetOf<String>()
        var total = 0L
        snapshot.files.forEach { file ->
            ProductId.of(file.product)
            val declaration = UpdateConfigurationBackup(Paths.get(file.root), listOf(file.relative))
            require(sources.add(checkedSource(declaration.root, declaration.files.single()))) { "duplicate configuration target" }
            require(file.size in 0..maximumFileBytes) { "invalid configuration size" }
            total += file.size
            require(total <= maximumTotalBytes) { "configuration snapshot exceeds size limit" }
            if (file.existed) {
                require(file.blob != null && file.blob.matches(Regex("[0-9]{4}\\.bin")) && blobs.add(file.blob)) { "invalid configuration blob" }
                val blob = UpdatePaths.inside(folder.resolve(file.blob), folder)
                require(Files.isRegularFile(blob, LinkOption.NOFOLLOW_LINKS) && Files.size(blob) == file.size) { "configuration backup size mismatch" }
                require(hash(blob) == file.sha256) { "configuration backup checksum mismatch" }
            } else require(file.blob == null && file.sha256 == null && file.size == 0L) { "invalid absent configuration record" }
        }
        return snapshot
    }

    private fun checkedSource(root: Path, relative: String): Path {
        UpdatePaths.inside(root, pluginsDirectory)
        val source = UpdatePaths.inside(root.resolve(relative), root)
        return UpdatePaths.inside(source, pluginsDirectory)
    }

    fun target(file: FileRecord): Path = checkedSource(Paths.get(file.root), file.relative)

    /** Copies a validated blob to a temporary file, checking its bytes again during the copy. */
    fun copyForRestore(id: UUID, file: FileRecord, temporary: Path) {
        require(file.existed && file.blob != null) { "configuration record has no backup content" }
        val folder = directory.toAbsolutePath().normalize().resolve(id.toString())
        val blob = UpdatePaths.inside(folder.resolve(file.blob), folder)
        val (size, digest) = copyBounded(blob, temporary, file.size, replaceExisting = true)
        require(size == file.size && digest == file.sha256) { "configuration backup changed during restoration" }
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
                    require(count <= maximum) { "configuration snapshot exceeds size limit" }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        FileChannel.open(target, StandardOpenOption.WRITE).use { it.force(true) }
        return count to hex(digest.digest())
    }

    private fun hash(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maximumFileBytes) { "configuration backup exceeds size limit" }
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
