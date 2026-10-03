package ru.privatenull.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderKey
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderRequest
import ru.privatenull.pnlibrary.api.plugin.PluginId

@Suppress("DEPRECATION")
class PlaceholderDefinitionBuilderTest {
    @Test
    fun `builder creates and installs a resolvable entry`() {
        val owner = PluginId.of("demo")
        var installed: PlaceholderEntry<String>? = null
        val builder = PlaceholderDefinitionBuilder(
            owner = owner,
            key = PlaceholderKey.of("status", String::class.java),
            placeholderApiEnabled = false,
            adapterLookup = { null },
            install = { installed = it },
        )

        val registration = builder.resolve { "online" }.register()
        val request = PlaceholderRequest(owner, owner, null, emptyMap(), emptyMap())

        assertEquals(registration, installed)
        assertEquals("online", installed!!.resolve(request).toCompletableFuture().join())
    }

    @Test
    fun `builder rejects registration without a resolver`() {
        val owner = PluginId.of("demo")
        val builder = PlaceholderDefinitionBuilder(
            owner = owner,
            key = PlaceholderKey.of("status", String::class.java),
            placeholderApiEnabled = false,
            adapterLookup = { null },
            install = {},
        )

        assertThrows(IllegalStateException::class.java) { builder.register() }
    }
}
