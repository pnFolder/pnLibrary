package org.pnfolder.pnlibrary.bukkit.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PublishedNamesTest {
    private val handlers = mapOf("war~score" to "plain", "war~score.{who}" to "player", "war~score.top" to "top", "tag" to "tag")

    @Test
    fun `a plain name matches exactly`() {
        assertEquals(PublishedNames.Found("plain", emptyMap()), PublishedNames.find(handlers, "war~score"))
    }

    @Test
    fun `a brace part reads the value from the text`() {
        assertEquals(PublishedNames.Found("player", mapOf("who" to "Alex")), PublishedNames.find(handlers, "war~score.Alex"))
    }

    @Test
    fun `fixed text beats a brace part`() {
        assertEquals("top", PublishedNames.find(handlers, "war~score.top")?.handler)
    }

    @Test
    fun `the rest after an underscore goes to the value`() {
        assertEquals(PublishedNames.Found("tag", mapOf("value" to "x")), PublishedNames.find(handlers, "tag_x"))
    }

    @Test
    fun `an unknown name gives nothing`() {
        assertNull(PublishedNames.find(handlers, "other"))
    }
}
