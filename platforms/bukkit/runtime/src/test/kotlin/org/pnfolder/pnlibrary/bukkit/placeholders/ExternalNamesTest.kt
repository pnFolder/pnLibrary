package org.pnfolder.pnlibrary.bukkit.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExternalNamesTest {
    @Test
    fun `brace parts take one part of the text each`() {
        assertEquals(mapOf("who" to "Alex"), ExternalNames.match("war~score.{who}.kills", "war~score.Alex.kills"))
    }

    @Test
    fun `fixed parts ignore case`() {
        assertEquals(mapOf("who" to "Alex"), ExternalNames.match("war~score.{who}", "WAR~Score.Alex"))
    }

    @Test
    fun `other shapes do not fit`() {
        assertNull(ExternalNames.match("war~score.{who}", "war~score"))
        assertNull(ExternalNames.match("war~score.{who}", "war~score.a.b"))
        assertNull(ExternalNames.match("war~score.{who}", "war~score."))
        assertNull(ExternalNames.match("war~score", "war~score"))
    }

    @Test
    fun `more fixed text is more specific`() {
        assertEquals(true, ExternalNames.specificity("a.top.{n}") > ExternalNames.specificity("a.{x}.{n}"))
    }
}
