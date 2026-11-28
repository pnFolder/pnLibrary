package org.pnfolder.pnlibrary.core.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.core.journal.view.JournalTime;
import org.pnfolder.pnlibrary.core.journal.view.JournalTrace;
import org.pnfolder.pnlibrary.core.journal.view.TraceLine;
import org.pnfolder.pnlibrary.core.journal.view.TraceView;

class JournalTimeAndTraceTest {
    private final long now = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli();
    private final ZoneId utc = ZoneOffset.UTC;
    private final ChatKit kit = new ChatKit(new ChatReader());

    private String ago(long millisBefore) {
        return JournalTime.ago(now - millisBefore, now, utc);
    }

    private String agoFrom(String date) {
        return JournalTime.ago(Instant.parse(date + "T12:00:00Z").toEpochMilli(), now, utc);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void agoIsJustNowUnderAMinuteAndNeverNegative() {
        assertEquals("только что", ago(30_000));
        assertEquals("только что", ago(-5_000));
    }

    @Test
    void agoInMinutesAndHoursUsesTheTwoLargestUnits() {
        assertEquals("5 мин назад", ago(5 * 60_000L));
        assertEquals("2 ч назад", ago(2 * 3_600_000L));
        assertEquals("3 ч 12 мин назад", ago(3 * 3_600_000L + 12 * 60_000L));
    }

    @Test
    void agoInDaysMonthsAndYearsDeclinesTheRussianUnits() {
        assertEquals("1 день назад", agoFrom("2026-10-19"));
        assertEquals("5 дней назад", agoFrom("2026-10-15"));
        assertEquals("3 месяца 2 дня назад", agoFrom("2026-07-18"));
        assertEquals("3 года 1 день назад", agoFrom("2023-10-19"));
    }

    @Test
    void spanNamesTheGapBetweenRepeats() {
        assertEquals("меньше минуты", JournalTime.span(30_000));
        assertEquals("5 мин", JournalTime.span(5 * 60_000L));
        assertEquals("1 ч 20 мин", JournalTime.span(80 * 60_000L));
        assertEquals("1 д 1 ч", JournalTime.span(1_500 * 60_000L));
    }

    @Test
    void oldMomentsAreDimmerThanRecentOnes() {
        long day = 86_400_000L;
        assertEquals(ChatPalette.SOFT, JournalTime.agoColor(now - 29 * day, now));
        assertEquals(ChatPalette.FAINT, JournalTime.agoColor(now - 31 * day, now));
    }

    @Test
    void aFrameOfThePluginIsBrightAndAFrameOfTheJdkIsFoldedAsFramework() {
        TraceLine own = JournalTrace.traceLine(kit, "at org.example.shop.Checkout.pay(Checkout.java:44)");
        TraceLine jdk = JournalTrace.traceLine(kit, "at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)");

        assertFalse(own.isFramework());
        assertTrue(jdk.isFramework());
        assertTrue(plain(own.getComponent()).contains("Checkout.pay"));
        assertTrue(plain(own.getComponent()).contains("Checkout.java:44"));
        assertTrue(plain(jdk.getComponent()).contains("ArrayList.forEach"));
    }

    @Test
    void exceptionLinesKeepTheirClassMessageAndCausePrefix() {
        String head = plain(JournalTrace.traceLine(kit, "java.lang.IllegalStateException: boom").getComponent());
        String cause = plain(JournalTrace.traceLine(kit, "Caused by: java.io.IOException: disk full").getComponent());

        assertTrue(head.contains("IllegalStateException") && head.contains("boom"));
        assertTrue(cause.contains("Caused by: ") && cause.contains("IOException") && cause.contains("disk full"));
    }

    @Test
    void chatFoldsConsecutiveFrameworkFramesIntoOneLineWhileTheConsoleShowsEveryLine() {
        List<String> trace = new ArrayList<>(Arrays.asList("java.lang.IllegalStateException: boom",
            "at org.example.shop.Checkout.pay(Checkout.java:44)"));
        for (int i = 1; i <= 5; i++) {
            trace.add("at java.base/java.util.Foo.bar" + i + "(Foo.java:" + i + ")");
        }
        trace.add("at org.example.shop.Main.run(Main.java:9)");

        TraceView chat = JournalTrace.traceView(kit, trace, false);
        TraceView console = JournalTrace.traceView(kit, trace, true);

        assertEquals(4, chat.getLines().size(), "head, own frame, folded line, own frame");
        assertTrue(plain(chat.getLines().get(2)).contains("5 служебных вызовов"));
        assertTrue(chat.isTrimmed(), "folded frames are reported so the card can offer the full trace");
        assertEquals(trace.size(), console.getLines().size());
        assertFalse(console.isTrimmed());
    }

    @Test
    void aVeryLongTraceInChatIsCutAndSaysHowManyLinesAreLeft() {
        List<String> trace = new ArrayList<>();
        trace.add("java.lang.IllegalStateException: boom");
        for (int i = 1; i <= 20; i++) {
            trace.add("at org.example.shop.Class" + i + ".method(File.java:" + i + ")");
        }
        TraceView chat = JournalTrace.traceView(kit, trace, false);

        assertTrue(chat.isTrimmed());
        assertEquals(JournalTrace.CARD_TRACE_LINES + 1, chat.getLines().size());
        assertTrue(plain(chat.getLines().get(chat.getLines().size() - 1)).contains("ещё"));
    }
}
