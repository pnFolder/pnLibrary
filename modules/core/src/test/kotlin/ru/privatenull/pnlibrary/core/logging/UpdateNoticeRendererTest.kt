package ru.privatenull.pnlibrary.core.logging

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateNoticeRendererTest {
    @Test
    fun `manual update notice describes versions channel compatibility and action`() {
        val lines = UpdateNoticeRenderer.render(
            UpdateNotice(
                product = "pnLibrary",
                currentVersion = "2.2.0-beta.9",
                latestVersion = "2.2.0",
                channel = "stable",
                minimumJava = 8,
                currentJava = 25,
                downloadUrl = "https://example.invalid/pnLibrary.jar",
                downloaded = false,
            ),
        )
        val plainText = lines.joinToString("\n").replace(Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE), "")

        assertTrue("2.2.0-beta.9" in plainText)
        assertTrue("2.2.0" in plainText)
        assertTrue("стабильный канал" in plainText)
        assertTrue("Java: 25" in plainText)
        assertTrue("Автоматическая загрузка отключена" in plainText)
        assertTrue("https://example.invalid/pnLibrary.jar" in plainText)
    }
}
