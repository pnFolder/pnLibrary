package org.pnfolder.pnlibrary.core.downloads.dependencies

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.downloads.FileDownloads
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.lang.reflect.Proxy
import java.net.URI

class DependencyDownloadReporterTest {
    @Test
    fun `renders staged dependency and human readable failure cards`() {
        val output = mutableListOf<String>()
        val platform = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PlatformAdapter::class.java)) { _, method, arguments ->
            when (method.name) {
                "getType" -> PlatformType.BUKKIT
                "ownerDetails" -> mapOf("name" to "Demo")
                "console" -> output += arguments!![1] as String
                "details" -> emptyMap<String, Any?>()
                else -> null
            }
        } as PlatformAdapter
        val reporter = DependencyDownloadReporter(platform, platform)
        val declaration = FileDownloads.builder().file("dependency:Vault") {
            it.url("https://example.org/Vault.jar")
                .destination(DownloadDestination.DATA_FOLDER, "Vault.jar")
        }.build().files.single()

        reporter.staged(Any(), listOf(declaration))
        reporter.failure(
            Any(),
            IllegalArgumentException("скачанный JAR объявляет Wrong, ожидался Vault"),
            LogLevel.ERROR,
        )

        assertTrue(output.any { it.contains("Vault.jar") })
        assertTrue(output.any { it.contains("В скачанном файле указано имя Wrong") })
    }

    private fun platformInto(output: MutableList<String>) =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PlatformAdapter::class.java)) { _, method, arguments ->
        when (method.name) {
            "getType" -> PlatformType.VELOCITY
            "ownerDetails" -> mapOf("name" to "Demo")
            "console" -> output += arguments!![1] as String
            "details" -> emptyMap<String, Any?>()
            else -> null
        }
    } as PlatformAdapter

    private fun group(): MissingGroup {
        val registration = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DownloadRegistration::class.java)) { _, _, _ -> null } as DownloadRegistration
        val vault = MissingDependency("Vault", "1.7.0", null, listOf(URI.create("https://example.org/Vault.jar")))
        return MissingGroup("Demo", "abc123", listOf(vault), registration)
    }

    @Test
    fun `the command in the advice is the one that works on this platform`() {
        val onProxy = mutableListOf<String>()
        platformInto(onProxy).let { DependencyDownloadReporter(it, it, "pnproxy").missing(Any(), group(), false) }
        val onServer = mutableListOf<String>()
        platformInto(onServer).let { DependencyDownloadReporter(it, it).missing(Any(), group(), false) }

        assertTrue(onProxy.any { it.contains("pnproxy deps install abc123") })
        assertTrue(onProxy.any { it.contains("pnproxy deps auto on") })
        assertTrue(onServer.any { it.contains("pn deps install abc123") && !it.contains("pnproxy") })
    }

    @Test
    fun `the notice tells what each plugin is for, whether it is required, and how its file is checked`() {
        val output = mutableListOf<String>()
        val registration = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DownloadRegistration::class.java)) { _, _, _ -> null } as DownloadRegistration
        val group = MissingGroup(
            "Demo", "abc123",
            listOf(
                MissingDependency(
                    "Vault", "1.7.0", null, listOf(URI.create("https://example.org/Vault.jar"), URI.create("https://mirror.example.org/Vault.jar")),
                    title = "Экономика", description = "Нужна для денег и магазинов.", required = true, checked = true,
                ),
                MissingDependency("Essentials", "2.20.0", URI.create("https://example.org/essentials"), emptyList(), required = false),
            ),
            registration,
        )

        platformInto(output).let { DependencyDownloadReporter(it, it).missing(Any(), group, false) }

        val text = output.joinToString(" ")
        assertTrue(text.contains("Обязательные"))
        assertTrue(text.contains("Необязательные"))
        assertTrue(text.contains("Экономика (Vault) 1.7.0+"))
        assertTrue(text.contains("Нужна для денег и магазинов."))
        assertTrue(text.contains("mirror.example.org/Vault.jar"))
        assertTrue(text.contains("SHA-256 автора"))
        assertTrue(text.contains("Essentials 2.20.0+"))
    }
}
