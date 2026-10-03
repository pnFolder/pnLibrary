package ru.privatenull.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import java.nio.file.Path

class DependencyDownloadRequestFactoryTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `creates a safe request only for missing automatic dependencies`() {
        val dependency = ExternalPluginDependency.builder("Unsafe Plugin!", "1.2.0")
            .url("https://example.org/plugin.jar")
            .automaticDownload(true)
            .build()
        val plan = DependencyDownloadRequestFactory(directory).create(listOf(dependency), emptyMap())

        requireNotNull(plan)
        assertEquals(directory.resolve("update"), plan.request.dataDirectory)
        assertEquals("Unsafe-Plugin.jar", plan.request.files.single().relativePath)
        assertEquals(dependency.external, plan.requirements["dependency:Unsafe Plugin!"])
    }

    @Test
    fun `does not request an already compatible dependency`() {
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.3")
            .url("https://example.org/Vault.jar")
            .automaticDownload(true)
            .build()

        assertNull(DependencyDownloadRequestFactory(directory).create(
            listOf(dependency),
            mapOf("vault" to "1.7.3"),
        ))
    }
}
