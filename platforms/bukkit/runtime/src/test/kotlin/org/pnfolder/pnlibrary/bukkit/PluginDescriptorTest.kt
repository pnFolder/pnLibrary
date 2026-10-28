package org.pnfolder.pnlibrary.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

class PluginDescriptorTest {
    @Test
    fun `plugin yml is valid and names the entry class`() {
        val stream = javaClass.classLoader.getResourceAsStream("plugin.yml")
        assertNotNull(stream, "plugin.yml is missing")
        val descriptor = stream!!.use { (Yaml().load(it) as Map<*, *>) }
        assertEquals("org.pnfolder.pnlibrary.bukkit.PnLibraryBukkitPlugin", descriptor["main"])
        assertNotNull(Class.forName(descriptor["main"].toString()))
    }
}
