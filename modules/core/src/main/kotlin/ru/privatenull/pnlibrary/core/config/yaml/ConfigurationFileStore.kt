package ru.privatenull.pnlibrary.core.config.yaml

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Owns durable file operations for one YAML configuration document.
 *
 * A replacement is validated while it is still a temporary file. The original
 * file therefore remains intact when encoding or decoding the new document fails.
 */
internal class ConfigurationFileStore(
    private val file: File,
    private val validator: (String) -> Unit,
) {
    /** Normalizes newlines and guarantees exactly one trailing newline. */
    fun normalize(content: String): String = content.replace("\r\n", "\n").trimEnd() + "\n"

    /** Atomically replaces the configuration after [validator] accepts it. */
    fun write(content: String) {
        val normalized = normalize(content)
        val temporary = Files.createTempFile(file.absoluteFile.parentFile.toPath(), "${file.name}.", ".tmp")
        try {
            Files.write(temporary, normalized.toByteArray(Charsets.UTF_8))
            validator(normalized)
            try {
                Files.move(
                    temporary,
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Saves the previous document and enforces bounded backup retention. */
    fun backup(content: String): File {
        val target = Files.createTempFile(
            file.absoluteFile.parentFile.toPath(),
            "${file.name}.before-sync-",
            ".bak",
        ).toFile()
        target.writeText(content, Charsets.UTF_8)
        existingBackups()
            .sortedByDescending(File::lastModified)
            .drop(MAX_BACKUPS)
            .forEach(::deleteBestEffort)
        return target
    }

    private fun existingBackups(): List<File> = file.absoluteFile.parentFile
        .listFiles { candidate ->
            candidate.name.startsWith("${file.name}.before-sync-") && candidate.name.endsWith(".bak")
        }
        ?.toList()
        .orEmpty()

    private fun deleteBestEffort(backup: File) {
        try {
            backup.delete()
        } catch (_: SecurityException) {
            // Retention cleanup must not turn a successful configuration load into a failure.
        }
    }

    private companion object {
        const val MAX_BACKUPS = 5
    }
}
