package ru.privatenull.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter

class PlaceholderRegistrationLifecycleTest {
    @Test
    fun `closed registration disappears and cannot be enabled again`() {
        val service = PlaceholderHub(TestPlatform).scope(PluginId.of("demo"), false)
        val registration = service.register("status", String::class.java) { it.resolve { "online" } }

        assertTrue(service.contains("status"))
        assertFalse(registration.isClosed)

        registration.close()
        registration.close()

        assertTrue(registration.isClosed)
        assertFalse(service.contains("status"))
        assertThrows(IllegalStateException::class.java) { registration.enable() }
    }

    private object TestPlatform : PlatformAdapter {
        override val type = PlatformType.BUKKIT
        override fun details(): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
