package org.pnfolder.pnlibrary.api.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination

class FileDownloadsTest {
    @Test
    fun `a file is manual and required until said otherwise`() {
        val files = FileDownloads.builder().file("geoip") { it.url("https://example.com/a.bin").destination(DownloadDestination.CACHE, "a.bin") }.build()
        val file = files.files.single()

        assertEquals(DownloadPolicy.MANUAL, file.policy)
        assertTrue(file.required)
    }

    @Test
    fun `policy and optional replace the flags`() {
        val files = FileDownloads.builder().file("geoip") {
            it.url("https://example.com/a.bin").destination(DownloadDestination.DATA_FOLDER, "a.bin").policy(DownloadPolicy.FORCED).optional()
        }.build()
        val file = files.files.single()

        assertEquals(DownloadPolicy.FORCED, file.policy)
        assertFalse(file.required)
    }

    @Test
    fun `a file can be declared with its address and path alone`() {
        val file = FileDownloads.builder().file("geoip", "https://example.com/geoip.mmdb", "geoip/geoip.mmdb").build().files.single()

        assertEquals("geoip", file.key)
        assertEquals(DownloadDestination.DATA_FOLDER, file.destination)
        assertEquals("geoip/geoip.mmdb", file.relativePath)
    }

    @Test
    fun `an address that is not https and a path outside the folder are refused`() {
        assertThrows(IllegalArgumentException::class.java) { FileDownloads.builder().file("a", "http://example.com/a", "a.bin") }
        assertThrows(IllegalArgumentException::class.java) { FileDownloads.builder().file("a", "https://example.com/a", "../a.bin") }
    }
}
