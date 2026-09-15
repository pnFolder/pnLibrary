package ru.privatenull.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaceholderPatternMatcherTest {
    @Test
    fun `captures multiple named segments`() {
        assertEquals(
            mapOf("player" to "Alex", "stat" to "kills"),
            PlaceholderPatternMatcher.match("player.{player}.stat.{stat}", "player.Alex.stat.kills"),
        )
    }

    @Test
    fun `treats literal punctuation as literal text`() {
        assertNull(PlaceholderPatternMatcher.match("value+{name}", "value-name"))
        assertEquals(mapOf("name" to "score"), PlaceholderPatternMatcher.match("value+{name}", "value+score"))
    }

    @Test
    fun `parameter never consumes another path segment`() {
        assertNull(PlaceholderPatternMatcher.match("player.{name}", "player.team.Alex"))
    }
}
