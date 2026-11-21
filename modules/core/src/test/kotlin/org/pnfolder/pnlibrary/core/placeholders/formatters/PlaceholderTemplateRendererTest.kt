package org.pnfolder.pnlibrary.core.placeholders.formatters

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class PlaceholderTemplateRendererTest {
    @Test
    fun `renderer handles conditions local expressions and ordinary expressions`() {
        val values = mapOf<String, Any?>(
            "enabled" to true,
            "status" to "online",
            "name" to "Alex",
        )
        val renderer = PlaceholderTemplateRenderer(
            resolve = { expression, _, _ ->
                val parts = expression.split('|')
                val value = values[parts.first()]
                CompletableFuture.completedFuture(if ("upper" in parts.drop(1)) value?.toString()?.uppercase() else value)
            },
            format = { value, _, _, _ -> value },
        )

        val rendered = renderer.render(
            "{?enabled}Server {status|upper}: {name}{:}offline{/}",
            playerId = null,
            values = emptyMap(),
        ).toCompletableFuture().join()

        assertEquals("Server ONLINE: Alex", rendered)
    }

    @Test
    fun `square brackets around tags stay text and ask no placeholder`() {
        val asked = mutableListOf<String>()
        val renderer = PlaceholderTemplateRenderer(
            resolve = { expression, _, _ -> asked += expression; CompletableFuture.completedFuture(null) },
            format = { value, _, _, _ -> value },
        )

        val text = "<gray>[<light_purple>pnTest</light_purple>]</gray> hi"
        val rendered = renderer.render(text, null, emptyMap()).toCompletableFuture().join()

        assertEquals(text, rendered)
        assertEquals(emptyList<String>(), asked)
    }
}
