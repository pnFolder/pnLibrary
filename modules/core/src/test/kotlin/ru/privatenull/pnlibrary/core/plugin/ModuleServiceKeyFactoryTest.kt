package ru.privatenull.pnlibrary.core.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginId

class ModuleServiceKeyFactoryTest {
    @Test
    fun `creates stable instance scoped keys`() {
        val nativePlugin = PluginId.of("example")
        val module = ModuleId.of("main")

        val first = ModuleServiceKeyFactory.create(7, nativePlugin, module)
        assertEquals(first, ModuleServiceKeyFactory.create(7, nativePlugin, module))
        assertNotEquals(first, ModuleServiceKeyFactory.create(8, nativePlugin, module))
        assertEquals("m-example-main-", first.value.substringBeforeLast('-') + "-")
    }
}
