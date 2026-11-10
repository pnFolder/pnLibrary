package org.pnfolder.pnlibrary.core.link

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class LinkSecretTest {
    @Test
    fun `creates the key once and reads the same key back`(@TempDir folder: Path) {
        val key = LinkSecret.readOrCreate(folder)
        assertNotNull(key)
        assertArrayEquals(key, LinkSecret.readOrCreate(folder))
    }

    @Test
    fun `the key file is private to its owner`(@TempDir folder: Path) {
        assumeTrue(folder.fileSystem.supportedFileAttributeViews().contains("posix"))
        LinkSecret.readOrCreate(folder)
        val permissions = Files.getPosixFilePermissions(folder.resolve(LinkSecret.FILE))
        assertEquals("rw-------", PosixFilePermissions.toString(permissions))
    }
}
