package ru.privatenull.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderRequest
import ru.privatenull.pnlibrary.api.plugin.PluginId

class PlaceholderFormatterRegistryTest {
    @Test
    fun `plugin formatter overrides built in formatter with the same name`() {
        val owner = PluginId.of("demo")
        val registry = PlaceholderFormatterRegistry()
        registry.registerBuiltIns()
        registry.register(owner, "upper", String::class.java) { value, _, _ -> "plugin:$value" }

        val result = registry.format(
            consumer = owner,
            value = "hello",
            pipeline = listOf("upper"),
            request = PlaceholderRequest(owner, owner, null, emptyMap(), emptyMap()),
        )

        assertEquals("plugin:hello", result)
    }

    @Test
    fun `default formatter supplies text for an absent value`() {
        val owner = PluginId.of("demo")
        val registry = PlaceholderFormatterRegistry()
        registry.registerBuiltIns()

        val result = registry.format(
            consumer = owner,
            value = null,
            pipeline = listOf("default:not available"),
            request = PlaceholderRequest(owner, owner, null, emptyMap(), emptyMap()),
        )

        assertEquals("not available", result)
    }
}
