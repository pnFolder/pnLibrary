package ru.privatenull.pnlibrary.api.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginDependencyTest {
    @Test
    fun `unified factories expose managed and external variants`() {
        val managed = Dependencies.managed("pnEconomy", "2.0.0", "pnFolder", "pnEconomy")
        assertNotNull(managed.managed)
        assertEquals("pneconomy", managed.managed!!.component.value)

        val external = Dependencies.plugin("Vault", "1.7.3", "https://example.org/vault")
        assertNotNull(external.external)
        assertEquals("Vault", external.external!!.plugin)
    }

    @Test
    fun `dependency can explicitly override automatic download policy`() {
        val dependency = Dependencies.plugin(
            "OptionalPlugin", "1.0.0", "https://example.org/plugin.jar",
            size = 4, sha256 = "00".repeat(32), required = false,
            automaticDownload = true, forceAutomaticDownload = true,
        )
        assertTrue(dependency.forceAutomaticDownload)
        assertTrue(dependency.external!!.forceAutomaticDownload)
    }
}
