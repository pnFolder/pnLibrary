package org.pnfolder.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.downloads.DownloadDestination
import org.pnfolder.pnlibrary.api.downloads.FileDownloads
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.lang.reflect.Proxy

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
}
