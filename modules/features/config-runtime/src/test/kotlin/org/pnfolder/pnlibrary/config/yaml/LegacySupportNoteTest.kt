package org.pnfolder.pnlibrary.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LegacySupportNoteTest {
    private val note = listOf(
        "# Если у вас возникли проблемы с плагином, пишите нам: мы всегда рады помочь.",
        "# If you have any problem with a plugin, write to us: we are always happy to help.",
        "# Discord: https://discord.gg/old",
        "# https://example.org/second-link",
    )

    @Test
    fun `the note is removed together with all its link lines and nothing else`() {
        val content = (listOf("_config-version: 1") + note + listOf("retries: 4", "")).joinToString("\n")
        assertEquals("_config-version: 1\nretries: 4\n", LegacySupportNote.removeFrom(content))
    }

    @Test
    fun `content without a note is returned unchanged`() {
        val content = "# my note\n# https://example.org/docs\nretries: 4\n"
        assertEquals(content, LegacySupportNote.removeFrom(content))
    }
}
