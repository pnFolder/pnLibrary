package ru.privatenull.pnlibrary.core.placeholders

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
            resolve = { expression, _, _ -> CompletableFuture.completedFuture(values[expression]) },
            format = { value, pipeline, _, _ ->
                if ("upper" in pipeline) value?.toString()?.uppercase() else value
            },
        )

        val rendered = renderer.render(
            "{?enabled}Server [status|upper]: {name}{:}offline{/}",
            playerId = null,
            values = emptyMap(),
        ).toCompletableFuture().join()

        assertEquals("Server ONLINE: Alex", rendered)
    }
}
