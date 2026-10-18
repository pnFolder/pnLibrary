package ru.privatenull.pnlibrary.common.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

class AtomicFilesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `write creates parent directories, replaces the target and leaves no temporary files`() {
        val target = directory.resolve("nested/state.json")
        AtomicFiles.write(target, "first".toByteArray())
        AtomicFiles.write(target, "second".toByteArray(), durable = true)
        assertEquals("second", String(Files.readAllBytes(target)))
        assertEquals(listOf("state.json"), Files.list(target.parent).use { files -> files.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `a failing writer keeps the previous content and removes its temporary file`() {
        val target = directory.resolve("state.json")
        AtomicFiles.write(target, "kept".toByteArray())
        assertThrows(IllegalStateException::class.java) {
            AtomicFiles.write(target) { error("broken writer") }
        }
        assertEquals("kept", String(Files.readAllBytes(target)))
        assertEquals(1L, Files.list(directory).use { it.count() })
    }

    @Test
    fun `move without replacing refuses an existing target`() {
        val source = Files.write(directory.resolve("source"), byteArrayOf(1))
        val target = Files.write(directory.resolve("target"), byteArrayOf(2))
        assertThrows(FileAlreadyExistsException::class.java) { AtomicFiles.move(source, target, replaceExisting = false) }
        AtomicFiles.move(source, target)
        assertEquals(1, Files.readAllBytes(target).single().toInt())
    }

    @Test
    fun `digests are lowercase hexadecimal and equal for bytes and files`() {
        val bytes = "abc".toByteArray()
        val file = Files.write(directory.resolve("data"), bytes)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Digests.sha256(bytes))
        assertEquals(Digests.sha256(bytes), Digests.sha256(file))
        assertEquals("00ff10", Digests.hex(byteArrayOf(0, -1, 16)))
    }
}
