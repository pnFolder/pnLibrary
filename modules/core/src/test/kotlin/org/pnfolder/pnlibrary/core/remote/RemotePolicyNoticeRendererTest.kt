package org.pnfolder.pnlibrary.core.remote

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata
import org.pnfolder.pnlibrary.api.remote.DenyAction
import org.pnfolder.pnlibrary.api.remote.RemotePolicyExplanation
import org.pnfolder.pnlibrary.api.remote.RemotePolicyResult
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.core.ui.ConsoleStyle
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.lang.reflect.Proxy

class RemotePolicyNoticeRendererTest {
    @Test
    fun `renders a denied policy with its complete explanation tree`() {
        val lines = mutableListOf<String>()
        val platform = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PlatformAdapter::class.java)) { _, method, arguments ->
            when (method.name) {
                "getType" -> PlatformType.BUKKIT
                "console" -> lines += arguments!![1] as String
                "details" -> emptyMap<String, Any?>()
                else -> null
            }
        } as PlatformAdapter
        val metadata = PluginMetadata(
            ModuleId.of("demo"), "Demo", "2.0.0", "pnFolder",
            PlatformType.BUKKIT, "Paper", "25", 25,
        )
        val policy = RemotePolicySettings.builder()
            .source("https://example.org/Policy.java")
            .onDeny(DenyAction.DISABLE_MODULE)
            .build()
        val explanation = RemotePolicyExplanation.builder("Требуется обновление")
            .child(RemotePolicyExplanation.builder("Версия устарела").child("Установлена 2.0.0").build())
            .build()

        RemotePolicyNoticeRenderer(platform).render(Any(), metadata, policy, false, explanation)

        assertTrue(lines.any { it.contains("ПРОВЕРКА СОВМЕСТИМОСТИ") })
        assertTrue(lines.any { it.contains("Требуется обновление") })
        assertTrue(lines.any { it.contains("Установлена 2.0.0") })
        assertTrue(lines.any { it.startsWith(ConsoleStyle.color(ChatPalette.DANGER)) && it.contains("Модуль безопасно остановлен") })
        assertTrue(lines.any { it.startsWith(ConsoleStyle.THEME.border) && it.contains("ПРОВЕРКА СОВМЕСТИМОСТИ") })
    }

    @Test
    fun `a decision card shows its own status, headline, details, steps and links`() {
        val lines = mutableListOf<String>()
        val platform = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PlatformAdapter::class.java)) { _, method, arguments ->
            if (method.name == "console") lines += arguments!![1] as String
            null
        } as PlatformAdapter
        val metadata = PluginMetadata(ModuleId.of("demo"), "Demo", "0.0.1", "pnFolder", PlatformType.BUKKIT, "Paper", "25", 25)
        val policy = RemotePolicySettings.builder().source("https://example.org/Policy.java").build()
        val result = RemotePolicyResult.denying("Версия устарела")
            .status("устарела").headline("нужно обновление")
            .detail("Минимальная", "0.0.2")
            .step("Скачайте новую версию")
            .link("Скачать", "https://example.org/download")
            .build()

        RemotePolicyNoticeRenderer(platform).render(Any(), metadata, policy, result)

        val text = lines.joinToString(separator = " | ")
        assertTrue(text.contains("устарела"), text)
        assertTrue(text.contains("нужно обновление"), text)
        assertTrue(text.contains("Минимальная") && text.contains("0.0.2"), text)
        assertTrue(text.contains("Скачайте новую версию"), text)
        assertTrue(text.contains("Скачать: https://example.org/download"), text)
        assertFalse(text.contains("не поддерживается"), text)
    }
}
