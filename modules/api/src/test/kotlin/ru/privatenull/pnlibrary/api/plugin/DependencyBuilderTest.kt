package ru.privatenull.pnlibrary.api.plugin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import ru.privatenull.pnlibrary.api.updates.ManagedProductDependency
import ru.privatenull.pnlibrary.api.version.SemanticVersion

class DependencyBuilderTest {
    @Test
    fun `external plugin accepts a direct URL without manual integrity metadata`() {
        val dependency = DependencyBuilder().apply {
            plugin("Vault", "1.7.3") {
                it.url("https://example.org/Vault.jar")
                    .automaticDownload(true)
            }
        }.build().single() as ExternalPluginDependency

        assertTrue(dependency.minimumVersion == SemanticVersion.parse("1.7.3"))
        assertTrue(dependency.artifact?.size == null)
        assertTrue(dependency.artifact?.sha256 == null)
        assertTrue(dependency.downloadPolicy == DownloadPolicy.AUTOMATIC)
    }

    @Test
    fun `managed product accepts versions inside its declared range`() {
        val builder = DependencyBuilder()
        builder.product("pneconomy") {
            it.minimumVersion("3.1.0")
                .maximumVersionExclusive("4.0.0")
                .github("pnFolder", "pnEconomy")
                .downloadPolicy(DownloadPolicy.AUTOMATIC)
        }

        val dependency = builder.build().single() as ManagedProductDependency
        assertTrue(dependency.versions.accepts(SemanticVersion.parse("3.1.0")))
        assertTrue(dependency.versions.accepts(SemanticVersion.parse("3.9.0")))
        assertFalse(dependency.versions.accepts(SemanticVersion.parse("4.0.0")))
    }

    @Test
    fun `manual download page cannot claim automatic installation`() {
        assertThrows(IllegalArgumentException::class.java) {
            DependencyBuilder().plugin("LuckPerms") {
                it.minimumVersion("5.4.0")
                    .downloadPage("https://luckperms.net/download")
                    .downloadPolicy(DownloadPolicy.AUTOMATIC)
            }
        }
    }

    @Test
    fun `exact external artifact can be forced`() {
        val builder = DependencyBuilder()
        builder.plugin("Legacy") {
            it.minimumVersion("2.0.0")
                .artifact("https://example.org/Legacy-2.0.0.jar", 128, "a".repeat(64))
                .downloadPolicy(DownloadPolicy.FORCED)
        }

        val dependency = builder.build().single() as ExternalPluginDependency
        assertTrue(dependency.artifact != null)
        assertTrue(dependency.downloadPolicy == DownloadPolicy.FORCED)
    }

    @Test
    fun `duplicate product ids are rejected after normalization`() {
        val builder = DependencyBuilder()
        builder.product("PnEconomy") { it.minimumVersion("1.0.0").github("pnFolder", "one") }
        assertThrows(IllegalArgumentException::class.java) {
            builder.product("pneconomy") { it.minimumVersion("2.0.0").github("pnFolder", "two") }
        }
    }
}
