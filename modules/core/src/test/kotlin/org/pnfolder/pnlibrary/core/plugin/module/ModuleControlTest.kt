package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.compat.MinecraftSupport
import org.pnfolder.pnlibrary.api.compat.NewerAction
import org.pnfolder.pnlibrary.api.compat.UnsupportedAction
import org.pnfolder.pnlibrary.api.libraries.LibraryState
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.core.plugin.LoadedPluginsTest
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

    private fun registry() = LoadedPluginsTest.registry()

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
        val registry = LoadedPluginsTest.registry(libraryFetcher = LibraryFetcher { _, _, _, _ -> error("offline") })

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
    fun `libraries declared in the registration builder are loaded before the module starts`() {
        val fetched = mutableListOf<String>()
        val registry = LoadedPluginsTest.registry(libraryFetcher = LibraryFetcher { _, _, _, library -> fetched += library.coordinates; null })

        val context = registry.register(Any()).registerModule("shop") { setup ->
            setup.library("org.xerial:sqlite-jdbc:3.46.0.0")
            setup.library("org.xerial:sqlite-jdbc:3.46.0.0")
            setup.library("com.example", "extra", "1") { it.optional().title("Extra") }
        }

        assertEquals(listOf("org.xerial:sqlite-jdbc:3.46.0.0", "com.example:extra:1"), fetched)
        assertEquals(2, context.libraries.loaded.size)
        registry.close()
    }

    @Test
    fun `defaults apply to libraries declared by coordinates and a false condition skips the download`() {
        val fetched = mutableListOf<ModuleLibrary>()
        val registry = LoadedPluginsTest.registry(libraryFetcher = LibraryFetcher { _, _, _, library -> fetched += library; null })

        registry.register(Any()).registerModule("shop") { setup ->
            setup.libraryDefaults { it.source { repository("https://mirror.example.org/maven/") }.mavenCentral(false).optional() }
            setup.library("org.xerial:sqlite-jdbc:3.46.0.0")
            setup.library(ModuleLibrary.of("com.example:skipped:1"), java.util.function.BooleanSupplier { false })
        }

        assertEquals(listOf("org.xerial:sqlite-jdbc:3.46.0.0"), fetched.map { it.coordinates })
        assertFalse(fetched.single().mavenCentral)
        assertFalse(fetched.single().required)
        assertEquals("https://mirror.example.org/maven/", fetched.single().sources.single().url)
        registry.close()
    }

    @Test
    fun `an administrator can switch a library off and on and retry a failed one`() {
        var offline = true
        val fetched = mutableListOf<String>()
        val registry = LoadedPluginsTest.registry(libraryFetcher = LibraryFetcher { _, _, _, library ->
            if (offline && library.artifact == "flaky") error("offline")
            fetched += library.coordinates
            null
        })
        val plugin = registry.register(Any())
        val context = plugin.registerModule("shop") { it.library(ModuleLibrary.builder("g", "flaky", "1").optional().build()); it.library("g:steady:1") }
        val flaky = ModuleLibrary.builder("g", "flaky", "1").build()
        val steady = ModuleLibrary.of("g:steady:1")

        assertEquals(LibraryState.FAILED, context.libraries.status(flaky)!!.state)
        assertEquals("offline", context.libraries.status(flaky)!!.error)
        assertEquals(LibraryState.FAILED, context.libraries.retry(flaky).state)
        offline = false
        assertEquals(LibraryState.LOADED, context.libraries.retry(flaky).state)

        val off = context.libraries.setAutoLoad(steady, false)
        assertTrue(off.restartRequired)
        assertFalse(off.autoLoad)
        assertEquals(LibraryState.LOADED, off.state)
        val on = context.libraries.setAutoLoad(steady, true)
        assertFalse(on.restartRequired)
        assertTrue(on.autoLoad)
        assertEquals(listOf("g:steady:1", "g:flaky:1"), fetched)
        registry.close()
    }

    @Test
    fun `a library switched off is not loaded at the next registration`() {
        val fetched = mutableListOf<String>()
        val registry = LoadedPluginsTest.registry(libraryFetcher = LibraryFetcher { _, _, _, library -> fetched += library.coordinates; null })
        val owner = Any()
        registry.register(owner).registerModule("shop") { it.library("g:a:1") }.libraries.setAutoLoad(ModuleLibrary.of("g:a:1"), false)
        fetched.clear()

        val again = registry.register(Any()).registerModule("shop") { it.library("g:a:1") }

        assertEquals(LibraryState.DISABLED, again.libraries.status(ModuleLibrary.of("g:a:1"))!!.state)
        assertTrue(fetched.isEmpty())
        registry.close()
    }

    private class Hooked : PluginModule {
        var enabled = false
        override fun onEnable(context: ModuleContext) {
            enabled = true
        }
    }

    @Test
    fun `a module outside its Minecraft versions is switched off with the reason and an administrator can force it`() {
        val registry = LoadedPluginsTest.registry(minecraftVersion = "1.21.11")
        val module = Hooked()

        val context = registry.register(Any()).registerModule("shop", module) { it.supportedMinecraft(MinecraftVersion.V1_16_5, MinecraftVersion.V1_20_4) }
        // the version 1.21.11 is newer than 1.20.4: the default only warns
        assertEquals(ModuleState.ENABLED, context.control.state)
        assertEquals(MinecraftSupport.NEWER_THAN_DECLARED, context.minecraft.support)

        val strict = registry.register(Any()).registerModule("strict", Hooked()) {
            it.supportedMinecraftOnly("1.20.1")
            it.onNewerMinecraft(NewerAction.BLOCK)
        }
        assertEquals(ModuleState.DISABLED, strict.control.state)
        assertTrue(strict.minecraft.blocked)
        assertNotNull(strict.control.lastError)

        (strict as MinecraftForcible).forceMinecraft(true)
        strict.control.enable()
        assertEquals(ModuleState.ENABLED, strict.control.state)
        registry.close()
    }

    @Test
    fun `a module without hooks and a stopped plugin refuse to register outside the versions`() {
        val registry = LoadedPluginsTest.registry(minecraftVersion = "1.12.2")

        assertThrows(IllegalStateException::class.java) {
            registry.register(Any()).registerModule("plain") { it.supportedMinecraftFrom(MinecraftVersion.V1_16_5) }
        }
        assertThrows(IllegalStateException::class.java) {
            registry.register(Any()).registerModule("stop", Hooked()) {
                it.supportedMinecraftFrom("1.16.5")
                it.onUnsupportedMinecraft(UnsupportedAction.STOP_PLUGIN)
            }
        }
        val warned = registry.register(Any()).registerModule("soft", Hooked()) {
            it.supportedMinecraftFrom("1.16.5")
            it.onUnsupportedMinecraft(UnsupportedAction.WARN_ONLY)
        }
        assertEquals(ModuleState.ENABLED, warned.control.state)
        registry.close()
    }

    @Test
    fun `a module without its own versions uses the plugin's`() {
        val registry = LoadedPluginsTest.registry(minecraftVersion = "1.12.2")
        val plugin = registry.register(Any())
        plugin.supportedMinecraftFrom(MinecraftVersion.V1_16_5)

        val inherited = plugin.registerModule("a", Hooked()) { }
        val own = plugin.registerModule("b", Hooked()) { it.supportedMinecraft(MinecraftVersion.V1_8_8, MinecraftVersion.V1_12_2) }

        assertEquals(ModuleState.DISABLED, inherited.control.state)
        assertEquals("1.16.5+", inherited.minecraft.declared)
        assertEquals(ModuleState.ENABLED, own.control.state)
        registry.close()
    }

    @Test
    fun `a required library declared in the builder stops the registration and an optional one only warns`() {
        val registry = LoadedPluginsTest.registry(libraryFetcher = LibraryFetcher { _, _, _, _ -> error("offline") })

        val failure = assertThrows(IllegalStateException::class.java) {
            registry.register(Any()).registerModule("needs") { it.library("com.example:missing:1") }
        }
        val started = registry.register(Any()).registerModule("wants") {
            it.library(ModuleLibrary.builder("com.example", "missing", "1").optional().build())
        }

        assertNotNull(failure.message)
        assertTrue(started.libraries.loaded.isEmpty())
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
