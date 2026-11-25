package org.pnfolder.pnlibrary.core.downloads.dependencies

import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency
import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.nio.file.Path

class DependencyDownloadRequestFactoryTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `creates a safe request only for missing automatic dependencies`() {
        val dependency = ExternalPluginDependency.builder("Unsafe Plugin!", "1.2.0")
            .url("https://example.org/plugin.jar")
            .downloadPolicy(DownloadPolicy.AUTOMATIC)
            .build()
        val plan = DependencyDownloadRequestFactory(directory.resolve("update"), PlatformType.BUKKIT) { "1.21.4" }.create(listOf(dependency), emptyMap())

        requireNotNull(plan)
        assertEquals(directory.resolve("update"), plan.request.dataDirectory)
        assertEquals("Unsafe-Plugin.jar", plan.request.files.single().relativePath)
        assertEquals(dependency.external, plan.requirements["dependency:Unsafe Plugin!"])
    }

    @Test
    fun `does not request an already compatible dependency`() {
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.3")
            .url("https://example.org/Vault.jar")
            .downloadPolicy(DownloadPolicy.AUTOMATIC)
            .build()

        assertNull(DependencyDownloadRequestFactory(directory.resolve("update"), PlatformType.BUKKIT) { "1.21.4" }.create(
            listOf(dependency),
            mapOf("vault" to "1.7.3"),
        ))
    }

    @Test
    fun `downloads the file made for this server and checks only its checksum`() {
        val old = "a".repeat(64)
        val modern = "b".repeat(64)
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.0")
            .variant("https://example.org/Vault-old.jar", old, minecraftBefore = "1.20")
            .variant("https://example.org/Vault-proxy.jar", "c".repeat(64), platforms = setOf(PlatformType.VELOCITY))
            .variant("https://example.org/Vault-new.jar", modern, minecraftFrom = "1.20", label = "для 1.20 и новее")
            .build()

        val plan = requireNotNull(
            DependencyDownloadRequestFactory(directory.resolve("update"), PlatformType.BUKKIT) { "1.21.4" }.create(listOf(dependency), emptyMap()),
        )
        val source = plan.request.files.single().source
        assertEquals("https://example.org/Vault-new.jar", source.uri.toString())
        assertEquals(modern, source.sha256)

        val older = DependencyDownloadRequestFactory(directory.resolve("update"), PlatformType.BUKKIT) { "1.16.5" }
        assertEquals(old, older.fileFor(dependency)?.sha256)
        val unknown = DependencyDownloadRequestFactory(directory.resolve("update"), PlatformType.BUNGEECORD) { null }
        assertNull(unknown.fileFor(dependency))
    }

    @Test
    fun `a file may carry its own size limit`() {
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.0")
            .url("https://example.org/Vault.jar")
            .maxBytes(2_000_000)
            .build()

        val plan = requireNotNull(
            DependencyDownloadRequestFactory(directory.resolve("update"), PlatformType.BUKKIT) { null }.create(listOf(dependency), emptyMap()),
        )

        assertEquals(2_000_000L, plan.request.files.single().source.maxBytes)
    }
}
