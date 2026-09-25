package ru.privatenull.pnlibrary.api.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.function.UnaryOperator

class ConfigApiConsistencyTest {
    @Test
    fun `configuration document returns a deeply immutable snapshot`() {
        val snapshot = ConfigDocument(mapOf("nested" to mapOf("items" to listOf("one")))).toMap()

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot as MutableMap<String, Any?>).clear()
        }
        val nested = snapshot.getValue("nested") as Map<*, *>
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (nested as MutableMap<Any?, Any?>).clear()
        }
        val items = nested["items"] as List<*>
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (items as MutableList<Any?>).clear()
        }
    }

    @Test
    fun `access policy exposes immutable snapshots and rejects blank patterns`() {
        val access = ConfigTypeAccess.builder().allow("shop").allowMatching("clan-*").build()

        assertTrue(access.allows(PluginId.of("owner"), PluginId.of("shop")))
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (access.allowedPlugins as MutableSet<PluginId>).add(PluginId.of("other"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConfigTypeAccess.builder().allowMatching(" ")
        }
    }

    @Test
    fun `configuration group closes once and rejects registrations after close`() {
        val first = TestConfig()
        val second = TestConfig()
        val group = ConfigGroup().add(first).add(second)

        assertEquals(listOf(first, second), group.all())
        group.close()
        group.close()

        assertTrue(group.isClosed)
        assertEquals(1, first.unloads)
        assertEquals(1, second.unloads)
        assertThrows(IllegalStateException::class.java) { group.add(TestConfig()) }
    }

    private class TestConfig : ManagedConfig<String> {
        var unloads = 0
        override val isLoaded = false
        override fun get() = "value"
        override fun load() = result()
        override fun reload() = result()
        override fun save() = Unit
        override fun save(value: String) = Unit
        override fun update(updater: UnaryOperator<String>): String = updater.apply("value")
        override fun validate(): List<ConfigProblem> = emptyList()
        override fun resetToDefaults() = "value"
        override fun unload() { unloads++ }
        private fun result() = ConfigLoadResult("value", emptyList(), null)
    }
}
