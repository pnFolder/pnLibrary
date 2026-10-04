package ru.privatenull.pnlibrary.core.remote

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.DenyAction
import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.plugin.RemotePolicy
import ru.privatenull.pnlibrary.api.remote.RemotePolicyExplanation
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
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
        val policy = RemotePolicy.builder()
            .source("https://example.org/policy.kt")
            .onDeny(DenyAction.DISABLE_MODULE)
            .build()
        val explanation = RemotePolicyExplanation.builder("Требуется обновление")
            .child(RemotePolicyExplanation.builder("Версия устарела").child("Установлена 2.0.0").build())
            .build()

        RemotePolicyNoticeRenderer(platform).render(Any(), metadata, policy, false, explanation)

        assertTrue(lines.any { it.contains("ПРОВЕРКА СОВМЕСТИМОСТИ") })
        assertTrue(lines.any { it.contains("Требуется обновление") })
        assertTrue(lines.any { it.contains("Установлена 2.0.0") })
        assertTrue(lines.any { it.contains("Модуль безопасно остановлен") })
    }
}
