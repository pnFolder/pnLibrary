package ru.privatenull.pnlibrary.api.config

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.plugin.PluginId

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
}
