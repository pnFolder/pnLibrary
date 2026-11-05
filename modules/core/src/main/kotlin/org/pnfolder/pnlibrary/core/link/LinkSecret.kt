package org.pnfolder.pnlibrary.core.link

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64

/**
 * The key the proxy and its game servers share, kept in `link.key` in the pnLibrary folder.
 *
 * The proxy makes the file when it is missing; a game server only reads it, so the link stays off until the
 * administrator copies the file from the proxy.
 */
object LinkSecret {
    /** Name of the key file. */
    const val FILE = "link.key"
    private const val KEY_BYTES = 32

    /** Reads the key from [folder], or returns null when there is no valid key file. */
    fun read(folder: Path): ByteArray? = runCatching {
        val text = String(Files.readAllBytes(folder.resolve(FILE)), Charsets.UTF_8).removePrefix(BOM).trim()
        Base64.getDecoder().decode(text).takeIf { it.size >= KEY_BYTES }
    }.getOrNull()

    /**
     * Reads the key from [folder] and makes a new one only when the file does not exist.
     *
     * A file that exists but cannot be read is left alone and the link stays off: a new key would silently cut
     * this proxy off from every server that already has the old one.
     */
    fun readOrCreate(folder: Path, warn: (String) -> Unit = {}): ByteArray? {
        val path = folder.resolve(FILE)
        if (Files.exists(path)) {
            return read(folder) ?: null.also { warn("$FILE exists but holds no valid key; the link stays off until it is fixed or deleted") }
        }
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        return runCatching {
            Files.createDirectories(folder)
            Files.write(path, (Base64.getEncoder().encodeToString(key) + '\n').toByteArray(Charsets.UTF_8))
            key
        }.getOrNull()
    }

    private const val BOM = "\uFEFF"
}
