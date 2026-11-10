package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files

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

    @Test
    fun `a resolver that throws is reported with its exception once`() {
        val failures = mutableListOf<Pair<String, Throwable>>()
        val service = PlaceholderHub(TestPlatform, failures = { message, error -> failures += message to error })
            .scope(PluginId.of("demo"), false)
        service.register("broken", String::class.java) {
            it.resolveAsync { java.util.concurrent.CompletableFuture.failedFuture(IllegalStateException("database is gone")) }
        }

        repeat(2) { service.resolve("broken").toCompletableFuture().join() }

        assertEquals(1, failures.size)
        assertEquals("database is gone", failures.single().second.message)
    }

    private object TestPlatform : PlatformAdapter {
        override val type = PlatformType.BUKKIT
        override val dataFolder = Files.createTempDirectory("pnlibrary-placeholder-test")
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
