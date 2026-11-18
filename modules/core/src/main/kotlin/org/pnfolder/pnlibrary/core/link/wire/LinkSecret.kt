package org.pnfolder.pnlibrary.core.link.wire

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64

// The proxy creates the file; game servers only read it, so the link stays off until the admin copies it over.
object LinkSecret {
    const val FILE = "link.key"
    private const val KEY_BYTES = 32

    fun read(folder: Path): ByteArray? = runCatching {
        val text = String(Files.readAllBytes(folder.resolve(FILE)), Charsets.UTF_8).removePrefix(BOM).trim()
        Base64.getDecoder().decode(text).takeIf { it.size >= KEY_BYTES }
    }.getOrNull()

    // A file that exists but is unreadable is left alone — overwriting it would silently break all servers that have the old key.
    fun readOrCreate(folder: Path, warn: (String) -> Unit = {}): ByteArray? {
        val path = folder.resolve(FILE)
        if (Files.exists(path)) {
            return read(folder) ?: null.also { warn("$FILE exists but holds no valid key; the link stays off until it is fixed or deleted") }
        }
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        return runCatching {
            Files.createDirectories(folder)
            create(path, (Base64.getEncoder().encodeToString(key) + '\n').toByteArray(Charsets.UTF_8))
            key
        }.getOrNull()
    }

    // Owner-only from the first byte, so the key is never readable by others between creation and chmod.
    private fun create(path: Path, content: ByteArray) {
        val posix = path.fileSystem.supportedFileAttributeViews().contains("posix")
        if (posix) {
            val owner = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
            Files.createFile(path, owner)
            Files.write(path, content, StandardOpenOption.WRITE)
        } else {
            Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        }
    }

    private const val BOM = "\uFEFF"
}
