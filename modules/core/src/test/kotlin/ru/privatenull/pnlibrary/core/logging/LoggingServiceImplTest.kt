package ru.privatenull.pnlibrary.core.logging

import net.kyori.adventure.text.format.NamedTextColor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.core.ui.ConsoleStyle
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files

class LoggingServiceImplTest {
    @Test
    fun `startup box uses the shared console palette`() {
        val platform = ConsolePlatform()
        LoggingServiceImpl(platform, platform, platform, platform)
            .box(this, "Demo", "1.0.0")
            .ok("Хранилище", "подключено")
            .warn("Кэш", "пуст")
            .show()

        val accent = ConsoleStyle.color(NamedTextColor.YELLOW)
        assertTrue(platform.lines.any { it.startsWith(accent) && it.contains("${ConsoleStyle.BOLD}PNFOLDER PLUGIN") })
        assertTrue(platform.lines.any { it.contains("Хранилище") && it.endsWith("${ConsoleStyle.color(NamedTextColor.GREEN)}${ConsoleStyle.BOLD}[ OK ]") })
        assertTrue(platform.lines.any { it.endsWith("${ConsoleStyle.color(NamedTextColor.GOLD)}${ConsoleStyle.BOLD}[ WARN ]") })
        val plain = platform.lines.joinToString("\n").replace(Regex("§[0-9a-fk-or]"), "")
        assertTrue("Demo успешно включён" in plain)
        assertTrue("└ пуст" in plain)
    }

    private class ConsolePlatform : PlatformAdapter {
        val lines = mutableListOf<String>()
        override val type = PlatformType.BUKKIT
        override val id = "test"
        override val dataFolder = Files.createTempDirectory("pnlibrary-box-test")
        override fun ownerDetails(owner: Any) = mapOf("name" to "Demo")
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) = Unit
        override fun console(owner: Any, message: String) {
            lines += message
        }
        override fun close() = Unit
    }
}
