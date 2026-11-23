package org.pnfolder.pnlibrary.api.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun `identifier parts can be read and written back`() {
        val id = ConfigId.parse("Quests/Main:rewards/daily.yml")

        assertEquals("quests", id.plugin.value)
        assertEquals("main", id.module.value)
        assertEquals(ConfigRoot.PLUGIN, id.root)
        assertEquals("rewards/daily.yml", id.path)
        assertEquals("daily.yml", id.fileName)
        assertEquals("rewards", id.directory)
        assertEquals("quests/main", id.moduleAddress.toString())
        assertEquals("quests/main:rewards/daily.yml", id.toString())
        assertEquals(id, ConfigId.of("quests", "main", "rewards\\daily.yml"))
    }

    @Test
    fun `identifier keeps a foreign root in the text`() {
        val id = ConfigId.parse("quests/main:@plugins/Essentials/config.yml")

        assertEquals(ConfigRoot.PLUGINS, id.root)
        assertEquals("Essentials/config.yml", id.path)
        assertEquals("quests/main:@plugins/Essentials/config.yml", id.toString())
        assertEquals(ConfigRoot.SERVER, ConfigId.parse("quests/main:@server/bukkit.yml").root)
    }

    @Test
    fun `address levels cover more or fewer files`() {
        val file = ConfigId.parse("quests/main:quests.yml")
        val other = ConfigId.parse("quests/extra:other.yml")

        val plugin = ConfigAddress.parse("quests")
        val module = ConfigAddress.parse("quests/main")
        val single = ConfigAddress.parse("quests/main:quests.yml")
        assertEquals(ConfigAddress.Level.PLUGIN, plugin.level)
        assertEquals(ConfigAddress.Level.MODULE, module.level)
        assertEquals(ConfigAddress.Level.FILE, single.level)
        assertTrue(plugin.matches(file) && plugin.matches(other))
        assertTrue(module.matches(file) && !module.matches(other))
        assertTrue(single.matches(file) && !single.matches(other))
        assertTrue(plugin.overlaps(single) && single.overlaps(plugin))
        assertTrue(!module.overlaps(ConfigAddress.parse("quests/extra")))
        assertTrue(!ConfigAddress.parse("other").overlaps(plugin))
    }

    @Test
    fun `malformed identifiers are rejected with a clear message`() {
        assertThrows(IllegalArgumentException::class.java) { ConfigId.parse("quests") }
        assertThrows(IllegalArgumentException::class.java) { ConfigId.parse("quests/main") }
        assertThrows(IllegalArgumentException::class.java) { ConfigAddress.parse("quests:file.yml") }
        assertThrows(IllegalArgumentException::class.java) { ConfigAddress.parse("a/b/c") }
        assertThrows(IllegalArgumentException::class.java) { ConfigAddress.parse(" ") }
    }
}
