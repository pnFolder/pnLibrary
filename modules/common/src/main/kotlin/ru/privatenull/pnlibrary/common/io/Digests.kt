package ru.privatenull.pnlibrary.common.io

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** SHA-256 digests as lowercase hexadecimal, the form used in release catalogs and integrity records. */
object Digests {
    /** The SHA-256 of [bytes]. */
    @JvmStatic
    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance(SHA_256).digest(bytes))

    /** The SHA-256 of the file at [path], read in chunks so large files are never loaded at once. */
    @JvmStatic
    fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance(SHA_256)
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    /** [bytes] as lowercase hexadecimal, two characters per byte. */
    @JvmStatic
    fun hex(bytes: ByteArray): String {
        val result = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            result[index * 2] = HEX[value ushr 4]
            result[index * 2 + 1] = HEX[value and 0x0F]
        }
        return String(result)
    }

    private const val SHA_256 = "SHA-256"
    private const val BUFFER_SIZE = 64 * 1024
    private val HEX = "0123456789abcdef".toCharArray()
}
