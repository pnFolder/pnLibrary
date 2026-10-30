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
        Base64.getDecoder().decode(String(Files.readAllBytes(folder.resolve(FILE)), Charsets.UTF_8).trim()).takeIf { it.size >= KEY_BYTES }
    }.getOrNull()

    /** Reads the key from [folder] and makes a new one if there is none. */
    fun readOrCreate(folder: Path): ByteArray? {
        read(folder)?.let { return it }
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        return runCatching {
            Files.createDirectories(folder)
            Files.write(folder.resolve(FILE), (Base64.getEncoder().encodeToString(key) + "\n").toByteArray(Charsets.UTF_8))
            key
        }.getOrNull()
    }
}
