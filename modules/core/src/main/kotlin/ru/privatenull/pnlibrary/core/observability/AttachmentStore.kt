package ru.privatenull.pnlibrary.core.observability

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

internal data class StoredAttachment(
    val id: String,
    val observationId: String,
    val originalName: String,
    val contentType: String,
    val size: Long,
    val sha256: String,
    val storedPath: Path,
)

internal class AttachmentStore(dataFolder: Path) {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val directory = dataFolder.resolve("observability").resolve("attachments")
    private val manifest = directory.resolve("manifest.json")
    private val attachments = linkedMapOf<String, StoredAttachment>()

    init {
        Files.createDirectories(directory)
        loadManifest()
    }

    @Synchronized
    fun save(observationId: String, source: Path): StoredAttachment {
        validate(source)
        val bytes = Files.readAllBytes(source)
        return save(
            observationId = observationId,
            name = source.fileName.toString(),
            contentType = contentType(source),
            bytes = bytes,
        )
    }

    @Synchronized
    fun save(
        observationId: String,
        name: String,
        contentType: String,
        bytes: ByteArray,
    ): StoredAttachment {
        validateFileName(name)
        val id = UUID.randomUUID().toString()
        val storedPath = directory.resolve("$id.bin")
        Files.write(storedPath, bytes)

        return StoredAttachment(
            id = id,
            observationId = observationId,
            originalName = name,
            contentType = contentType,
            size = bytes.size.toLong(),
            sha256 = sha256(bytes),
            storedPath = storedPath,
        ).also { attachment ->
            attachments[id] = attachment
            writeManifest()
        }
    }

    @Synchronized
    fun forObservation(observationId: String): List<StoredAttachment> =
        attachments.values.filter { attachment -> attachment.observationId == observationId }

    @Synchronized
    fun all(): List<StoredAttachment> = attachments.values.toList()

    fun read(attachment: StoredAttachment): ByteArray = Files.readAllBytes(attachment.storedPath)

    @Synchronized
    fun removeOrphans(activeObservationIds: Set<String>) {
        val orphanIds = attachments.values
            .filterNot { it.observationId in activeObservationIds }
            .map { it.id }

        orphanIds.forEach { id ->
            val attachment = attachments.remove(id) ?: return@forEach
            Files.deleteIfExists(attachment.storedPath)
        }
        if (orphanIds.isNotEmpty()) writeManifest()
    }

    fun clear() = removeOrphans(emptySet())

    private fun validate(source: Path) {
        require(Files.isRegularFile(source)) {
            "Attachment is not a readable file: $source"
        }
        validateFileName(source.fileName.toString())
    }

    private fun validateFileName(name: String) {
        val extension = name.substringAfterLast('.', "").lowercase()
        require(extension !in blockedExtensions) { "Executable attachments are not allowed: $name" }
    }

    private fun loadManifest() {
        if (!Files.isRegularFile(manifest)) return
        val type = object : TypeToken<List<AttachmentManifestEntry>>() {}.type
        val entries = runCatching {
            gson.fromJson<List<AttachmentManifestEntry>>(Files.readString(manifest), type)
        }.getOrDefault(emptyList())
        entries.mapNotNull(::restore).forEach { attachment -> attachments[attachment.id] = attachment }
    }

    private fun restore(entry: AttachmentManifestEntry): StoredAttachment? {
        val storedPath = directory.resolve("${entry.id}.bin")
        if (!Files.isRegularFile(storedPath)) return null
        return StoredAttachment(
            id = entry.id,
            observationId = entry.observationId,
            originalName = entry.originalName,
            contentType = entry.contentType,
            size = entry.size,
            sha256 = entry.sha256,
            storedPath = storedPath,
        )
    }

    private fun writeManifest() {
        val entries = attachments.values.map { attachment ->
            AttachmentManifestEntry(
                attachment.id,
                attachment.observationId,
                attachment.originalName,
                attachment.contentType,
                attachment.size,
                attachment.sha256,
            )
        }
        Files.writeString(manifest, gson.toJson(entries))
    }

    private fun contentType(path: Path): String = Files.probeContentType(path) ?: when (path.extension.lowercase()) {
        "log", "txt" -> "text/plain"
        "json" -> "application/json"
        "yml", "yaml" -> "application/yaml"
        "xml" -> "application/xml"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private val Path.extension: String
        get() = fileName.toString().substringAfterLast('.', "")

    private companion object {
        val blockedExtensions = setOf("jar", "exe", "dll", "so", "bat", "cmd", "sh", "ps1")
    }
}

private data class AttachmentManifestEntry(
    val id: String,
    val observationId: String,
    val originalName: String,
    val contentType: String,
    val size: Long,
    val sha256: String,
)
