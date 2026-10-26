package org.pnfolder.pnlibrary.common.io

import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path

/** Text-file operations that retain the runtime's Java 8 compatibility. */
object TextFiles {
    /** Reads the whole file as text in [charset]. */
    fun read(path: Path, charset: Charset = Charsets.UTF_8): String =
        Files.newBufferedReader(path, charset).use { it.readText() }

    /** Writes [text] to [path] encoded with [charset] and returns [path]; [options] are passed to [Files.write]. */
    fun write(path: Path, text: CharSequence, charset: Charset = Charsets.UTF_8, vararg options: OpenOption): Path {
        val encoded = charset.newEncoder().encode(CharBuffer.wrap(text))
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        return Files.write(path, bytes, *options)
    }
}
