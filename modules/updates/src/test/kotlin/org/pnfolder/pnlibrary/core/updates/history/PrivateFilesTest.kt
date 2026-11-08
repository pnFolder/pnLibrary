package org.pnfolder.pnlibrary.core.updates.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class PrivateFilesTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `files and folders are private and an old file is replaced`() {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        val folder = directory.resolve("a/b")
        PrivateFiles.directories(folder)
        val file = folder.resolve("x.gz")
        Files.write(file, ByteArray(5))
        PrivateFiles.output(file).use { it.write(1) }
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(folder)))
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        assertEquals(1, Files.size(file))
    }
}
