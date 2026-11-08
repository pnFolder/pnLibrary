package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.common.io.AtomicFiles
import java.io.File
import java.nio.file.Files

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
    /** Converts line endings to a single newline and ends the text with exactly one newline. */
    fun normalize(content: String): String = content.replace("\r\n", "\n").trimEnd() + "\n"

    /** Replaces the file with [content] atomically, after the validator accepted it as a temporary file. */
    fun write(content: String) {
        val normalized = normalize(content)
        val temporary = Files.createTempFile(file.absoluteFile.parentFile.toPath(), "${file.name}.", ".tmp")
        try {
            Files.write(temporary, normalized.toByteArray(Charsets.UTF_8))
            validator(normalized)
            AtomicFiles.move(temporary, file.toPath())
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Saves the previous document beside the file, keeping the newest [MAX_BACKUPS] copies. */
    fun backup(content: String): File {
        val target = Files.createTempFile(
            file.absoluteFile.parentFile.toPath(),
            "${file.name}.before-sync-",
            ".bak",
        ).toFile()
        try {
            target.writeText(content, Charsets.UTF_8)
        } catch (error: Throwable) {
            // An empty backup would count against the retention limit and push out a real one.
            deleteBestEffort(target)
            throw error
        }
        // The new backup is kept explicitly: modification times have coarse resolution on some file systems,
        // and a tie must not make the backup just written the one that gets deleted.
        existingBackups()
            .filter { it != target }
            .sortedByDescending(File::lastModified)
            .drop(MAX_BACKUPS - 1)
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
