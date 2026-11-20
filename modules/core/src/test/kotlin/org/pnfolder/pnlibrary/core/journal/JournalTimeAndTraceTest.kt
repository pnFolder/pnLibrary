package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import java.time.Instant
import java.time.ZoneOffset

class JournalTimeAndTraceTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli()
    private val utc = ZoneOffset.UTC
    private val kit = ChatKit(ChatReader())

    private fun ago(millisBefore: Long) = ago(now - millisBefore, now, utc)

    private fun agoFrom(date: String) = ago(Instant.parse("${date}T12:00:00Z").toEpochMilli(), now, utc)

    private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)

    @Test
    fun `ago is just now under a minute and never negative`() {
        assertEquals("только что", ago(30_000))
        assertEquals("только что", ago(-5_000))
    }

    @Test
    fun `ago in minutes and hours uses the two largest units`() {
        assertEquals("5 мин назад", ago(5 * 60_000L))
        assertEquals("2 ч назад", ago(2 * 3_600_000L))
        assertEquals("3 ч 12 мин назад", ago(3 * 3_600_000L + 12 * 60_000L))
    }

    @Test
    fun `ago in days months and years declines the Russian units`() {
        assertEquals("1 день назад", agoFrom("2026-10-19"))
        assertEquals("5 дней назад", agoFrom("2026-10-15"))
        assertEquals("3 месяца 2 дня назад", agoFrom("2026-07-18"))
        assertEquals("3 года 1 день назад", agoFrom("2023-10-19"))
    }

    @Test
    fun `span names the gap between repeats`() {
        assertEquals("меньше минуты", span(30_000))
        assertEquals("5 мин", span(5 * 60_000L))
        assertEquals("1 ч 20 мин", span(80 * 60_000L))
        assertEquals("1 д 1 ч", span(1_500 * 60_000L))
    }

    @Test
    fun `old moments are dimmer than recent ones`() {
        val day = 86_400_000L
        assertEquals(ChatPalette.SOFT, agoColor(now - 29 * day, now))
        assertEquals(ChatPalette.FAINT, agoColor(now - 31 * day, now))
    }

    @Test
    fun `a frame of the plugin is bright and a frame of the JDK is folded as framework`() {
        val own = traceLine(kit, "at org.example.shop.Checkout.pay(Checkout.java:44)")
        val jdk = traceLine(kit, "at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)")

        assertFalse(own.second)
        assertTrue(jdk.second)
        assertTrue(plain(own.first).contains("Checkout.pay"))
        assertTrue(plain(own.first).contains("Checkout.java:44"))
        assertTrue(plain(jdk.first).contains("ArrayList.forEach"))
    }

    @Test
    fun `exception lines keep their class message and cause prefix`() {
        val head = plain(traceLine(kit, "java.lang.IllegalStateException: boom").first)
        val cause = plain(traceLine(kit, "Caused by: java.io.IOException: disk full").first)

        assertTrue(head.contains("IllegalStateException") && head.contains("boom"))
        assertTrue(cause.contains("Caused by: ") && cause.contains("IOException") && cause.contains("disk full"))
    }

    @Test
    fun `chat folds consecutive framework frames into one line while the console shows every line`() {
        val trace = listOf("java.lang.IllegalStateException: boom", "at org.example.shop.Checkout.pay(Checkout.java:44)") +
            (1..5).map { "at java.base/java.util.Foo.bar$it(Foo.java:$it)" } +
            "at org.example.shop.Main.run(Main.java:9)"

        val (chat, chatTrimmed) = traceView(kit, trace, full = false)
        val (console, consoleTrimmed) = traceView(kit, trace, full = true)

        assertEquals(4, chat.size, "head, own frame, folded line, own frame")
        assertTrue(plain(chat[2]).contains("5 служебных вызовов"))
        assertTrue(chatTrimmed, "folded frames are reported so the card can offer the full trace")
        assertEquals(trace.size, console.size)
        assertFalse(consoleTrimmed)
    }

    @Test
    fun `a very long trace in chat is cut and says how many lines are left`() {
        val frames = (1..20).map { "at org.example.shop.Class$it.method(File.java:$it)" }
        val (chat, trimmed) = traceView(kit, listOf("java.lang.IllegalStateException: boom") + frames, full = false)

        assertTrue(trimmed)
        assertEquals(CARD_TRACE_LINES + 1, chat.size)
        assertTrue(plain(chat.last()).contains("ещё"))
    }
}
