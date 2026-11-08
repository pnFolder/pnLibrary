package org.pnfolder.pnlibrary.core.updates.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI

class TrustedHostsTest {
    @Test
    fun `github release and cdn links pass`() {
        listOf(
            "https://github.com/PnFolder/pnLibrary/releases/download/v1/pnLibrary-1.0.0-universal.jar",
            "https://objects.githubusercontent.com/x/y.jar",
            "https://release-assets.githubusercontent.com/x/y.jar",
            "https://raw.githubusercontent.com/PnFolder/pnLibrary/main/x.jar",
            "https://cdn.jsdelivr.net/gh/PnFolder/pnLibrary@main/x.jar",
        ).forEach { assertEquals(URI(it), TrustedHosts.requireDownload(URI(it))) }
    }

    @Test
    fun `foreign host and plain http are refused`() {
        assertThrows(IllegalArgumentException::class.java) { TrustedHosts.requireDownload(URI("https://evil.example.com/a.jar")) }
        assertThrows(IllegalArgumentException::class.java) { TrustedHosts.requireDownload(URI("https://github.com.evil.com/a.jar")) }
        assertThrows(IllegalArgumentException::class.java) { TrustedHosts.requireDownload(URI("http://github.com/a.jar")) }
        assertThrows(IllegalArgumentException::class.java) { TrustedHosts.requireRedirect(URI("https://evil.example.com/a.jar")) }
    }
}
