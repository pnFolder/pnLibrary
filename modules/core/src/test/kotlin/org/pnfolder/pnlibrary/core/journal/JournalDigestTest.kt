package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class JournalDigestTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli()
    private val started = now - Duration.ofHours(3).toMillis()

    private fun record(level: JournalLevel, source: String, message: String, at: Long) =
        JournalRecord(at, level, source, "LOG", message)

    private fun lines(records: List<JournalRecord>): List<String> =
        joinDigest(records, now, 24, started).map { PlainTextComponentSerializer.plainText().serialize(it) }

    @Test
    fun `a quiet journal shows nothing`() {
        assertEquals(emptyList<Any>(), joinDigest(emptyList(), now, 24, started))
        assertEquals(emptyList<Any>(), joinDigest(listOf(record(JournalLevel.INFO, "shop", "opened", now)), now, 24, started))
    }

    @Test
    fun `the most urgent events lead and each gets a word for why`() {
        val records = listOf(
            record(JournalLevel.ERROR, "pnShop", "database locked", started + 30_000),
            record(JournalLevel.ERROR, "pnAuth", "login failed", now - Duration.ofMinutes(2).toMillis()),
        ) + (1..6).map { record(JournalLevel.WARNING, "pnCaptcha", "slow answer", now - Duration.ofHours(it.toLong()).toMillis() - 1) }

        val shown = lines(records)
        val text = shown.joinToString("\n")

        assertTrue(shown.any { it.contains("pnShop") && it.contains("старт") }, text)
        assertTrue(shown.any { it.contains("pnAuth") && it.contains("только что") }, text)
        assertFalse(shown.any { it.contains("pnCaptcha") && it.contains("slow answer") }, "only two events are listed: $text")
        assertTrue(text.contains("2 ошибки"), text)
        assertTrue(text.contains("6 предупреждений"), text)
        assertTrue(text.contains("Проверьте, что плагин подходит к версии сервера."), text)
    }

    @Test
    fun `a recurring warning is marked and warnings alone raise no alarm`() {
        val recurring = (1..6).map { record(JournalLevel.WARNING, "pnCaptcha", "slow answer", now - Duration.ofHours(it.toLong()).toMillis() - 1) }
        val shown = lines(recurring).joinToString("\n")
        assertTrue(shown.contains("pnCaptcha ×6 повтор"), shown)

        val single = lines(listOf(record(JournalLevel.WARNING, "pnMap", "slow tile", now - Duration.ofHours(5).toMillis()))).joinToString("\n")
        assertTrue(single.contains("Ничего срочного"), single)
        assertFalse(single.contains("подробнее"), single)
    }

    @Test
    fun `a fresh event that repeats gets the advice for repeating problems and fits one chat line`() {
        val records = (1..16).map { record(JournalLevel.ERROR, "pnLibrary", "Error occurred while enabling pnLibraryAcceptance v2.2.0-beta.6 (Is it up to date?)", now - it * 30_000L) }

        val shown = lines(records)
        val text = shown.joinToString("\n")

        assertTrue(text.contains("Покажите карточку автору плагина"), text)
        assertFalse(text.contains("Посмотрите, что делали игроки"), text)
        assertTrue(shown.filter { it.contains("pnLibrary ×16") }.all { it.length <= 52 }, text)
    }

    @Test
    fun `the strip has one column per slice of the window and ends at now`() {
        val strip = lines(listOf(record(JournalLevel.ERROR, "pnShop", "locked", now - 1_000)))
            .first { it.endsWith("сейчас") }

        val columns = strip.trim().removeSuffix("сейчас").trim()

        assertEquals(24, columns.length)
        assertEquals('▁', columns.last())
        assertEquals(23, columns.count { it == '·' })
    }
}
