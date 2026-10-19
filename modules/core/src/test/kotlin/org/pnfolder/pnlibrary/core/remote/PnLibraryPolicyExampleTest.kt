package org.pnfolder.pnlibrary.core.remote

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.*
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import java.nio.file.Files
import java.nio.file.Paths

class PnLibraryPolicyExampleTest {
    @Test
    fun `actual Kotlin example compiles and explains each demo refusal`() {
        val file = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
            .map { it.resolve(".pnlibrary/policies/PnLibraryPolicy.kt") }
            .firstOrNull { Files.isRegularFile(it) }
            ?: error("Missing PnLibraryPolicy.kt example")
        val source = Files.readAllBytes(file)
        fun check(version: String, product: String = "pnlibrary", maintenance: Boolean = false,
                  platform: PlatformType = PlatformType.BUKKIT): RemotePolicyResult {
            val context = RemotePolicyContext.builder()
                .product(ProductInfo(product, "pnLibrary", version))
                .platform(PlatformInfo(platform, platform.name, "demo"))
                .server(ServerInfo("demo", MinecraftVersion.parseInfo(null)))
                .value("maintenance", maintenance.toString()).build()
            return RemotePolicyEngine.checkSource(source, context, "kt")
        }

        for ((version, expected) in listOf(
            "0.9.0" to "вне поддерживаемого диапазона",
            "2.0.0" to "вне поддерживаемого диапазона",
            "1.2.0-beta.1" to "Тестовые сборки запрещены",
            "1.1.3+build.2" to "сборка отозвана",
            "unknown" to "Не удалось определить версию",
        )) {
            val result = check(version)
            assertFalse(result.allowed, version)
            assertTrue(result.explanation.children.any { it.text.contains(expected) }, version)
            assertTrue(result.explanation.children.any { it.text == "Что делать" })
        }
        assertFalse(check("1.2.0", product = "other-plugin").allowed)
        assertFalse(check("1.2.0", maintenance = true).allowed)
        for (platform in listOf(PlatformType.BUKKIT, PlatformType.VELOCITY, PlatformType.BUNGEECORD)) {
            assertTrue(check("1.2.0", platform = platform).allowed, platform.name)
        }
    }
}
