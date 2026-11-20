package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryImplTest
import java.nio.file.Path

class ModuleControlTest {
    private class Recording(private val failOn: String? = null, private val inPlace: Boolean = false) : PluginModule {
        val calls = mutableListOf<String>()
        private fun step(name: String) {
            calls += name
            if (name == failOn) error("boom in $name")
        }
        override fun onEnable(context: ModuleContext) = step("enable")
        override fun onDisable(context: ModuleContext) = step("disable")
        override fun onReload(context: ModuleContext): Boolean {
            step("reload")
            return inPlace
        }
    }

    private fun registry() = PluginRegistryImplTest.registry()

    @Test
    fun `a module starts at registration and can be disabled, enabled and restarted`() {
        val module = Recording()
        val registry = registry()
        val context = registry.register(Any()).registerModule("shop", module)

        assertEquals(ModuleState.ENABLED, context.control.state)
        context.control.disable()
        assertEquals(ModuleState.DISABLED, context.control.state)
        context.control.enable()
        context.control.restart()
        assertEquals(listOf("enable", "disable", "enable", "disable", "enable"), module.calls)
        assertEquals(ModuleState.ENABLED, context.control.state)
        registry.close()
    }

    @Test
    fun `reload restarts a module that cannot reload in place`() {
        val stays = Recording(inPlace = true)
        val restarts = Recording(inPlace = false)
        val registry = registry()
        registry.register(Any()).registerModule("one", stays).control.reload()
        registry.register(Any()).registerModule("two", restarts).control.reload()

        assertEquals(listOf("enable", "reload"), stays.calls)
        assertEquals(listOf("enable", "reload", "disable", "enable"), restarts.calls)
        registry.close()
    }

    @Test
    fun `a failing hook leaves the module failed with the error and enabling tries again`() {
        val module = Recording(failOn = "enable")
        val registry = registry()
        val context = registry.register(Any()).registerModule("shop", module)

        assertEquals(ModuleState.FAILED, context.control.state)
        assertNotNull(context.control.lastError)
        context.control.disable()
        assertEquals(ModuleState.DISABLED, context.control.state)
        assertNull(context.control.lastError)
        registry.close()
    }

    @Test
    fun `a module that an administrator keeps off does not start with the server`(@TempDir directory: Path) {
        val file = directory.resolve("modules").resolve("preferences.json")
        val first = ModulePreferences(file)
        first.setAutoStart("shop", false)

        assertFalse(ModulePreferences(file).autoStart("shop"))
        assertTrue(ModulePreferences(file).autoStart("other"))
        first.setAutoStart("shop", true)
        assertTrue(ModulePreferences(file).autoStart("shop"))
    }

    @Test
    fun `a module without hooks is listed but cannot be switched`() {
        val registry = registry()
        val context = registry.register(Any()).registerModule("plain")

        assertFalse(context.control.isControllable)
        assertEquals(ModuleState.ENABLED, context.control.state)
        assertThrows(UnsupportedOperationException::class.java) { context.control.disable() }
        registry.close()
    }

    @Test
    fun `closing the plugin runs the disable hook once`() {
        val module = Recording()
        val registry = registry()
        registry.register(Any()).registerModule("shop", module)
        registry.close()

        assertEquals(listOf("enable", "disable"), module.calls)
    }

    private class WithLibrary(private val library: ModuleLibrary) : PluginModule {
        var enabled = false
        override fun libraries() = listOf(library)
        override fun onEnable(context: ModuleContext) {
            enabled = true
        }
    }

    @Test
    fun `a required library that cannot be loaded fails the module and an optional one does not`() {
        val required = WithLibrary(ModuleLibrary.of("com.example:missing:1"))
        val optional = WithLibrary(ModuleLibrary.builder("com.example", "missing", "1").optional().build())
        val registry = PluginRegistryImplTest.registry(libraryFetcher = LibraryFetcher { _, _, _, _ -> error("offline") })

        val failed = registry.register(Any()).registerModule("needs", required)
        val started = registry.register(Any()).registerModule("wants", optional)

        assertEquals(ModuleState.FAILED, failed.control.state)
        assertFalse(required.enabled)
        assertNotNull(failed.control.lastError)
        assertEquals(ModuleState.ENABLED, started.control.state)
        assertTrue(optional.enabled)
        registry.close()
    }

    @Test
    fun `the library lists every module and every library with who registered it`() {
        val registry = registry()
        val plugin = registry.register(Any())
        plugin.registerModule("one", Recording()).libraries.load(ModuleLibrary.of("g:a:1"))
        plugin.registerModule("two", Recording()).libraries.load(ModuleLibrary.of("g:a:2"))

        assertEquals(2, registry.allModules().size)
        assertEquals(listOf("one", "two"), registry.libraryUsage().map { it.module?.value }.sortedBy { it })
        assertEquals(2, plugin.allLibraries.size)
        registry.close()
    }
}
