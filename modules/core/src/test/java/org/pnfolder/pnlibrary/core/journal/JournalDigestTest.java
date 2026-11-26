package org.pnfolder.pnlibrary.core.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.journal.view.JournalDigest;

class JournalDigestTest {
    private final long now = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli();
    private final long started = now - Duration.ofHours(3).toMillis();

    private static JournalRecord record(JournalLevel level, String source, String message, long at) {
        return new JournalRecord(at, level, source, "LOG", message);
    }

    private List<String> lines(List<JournalRecord> records) {
        List<String> result = new ArrayList<String>();
        for (Component line : JournalDigest.join(records, now, 24, started)) {
            result.add(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return result;
    }

    private List<JournalRecord> warnings(String source, String message, int count) {
        List<JournalRecord> records = new ArrayList<JournalRecord>();
        for (int hour = 1; hour <= count; hour++) {
            records.add(record(JournalLevel.WARNING, source, message, now - Duration.ofHours(hour).toMillis() - 1));
        }
        return records;
    }

    private static boolean any(List<String> lines, String... parts) {
        for (String line : lines) {
            boolean all = true;
            for (String part : parts) {
                all &= line.contains(part);
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aQuietJournalShowsNothing() {
        assertEquals(Collections.emptyList(), JournalDigest.join(Collections.<JournalRecord>emptyList(), now, 24, started));
        assertEquals(Collections.emptyList(), JournalDigest.join(
            Collections.singletonList(record(JournalLevel.INFO, "shop", "opened", now)), now, 24, started));
    }

    @Test
    void theMostUrgentEventsLeadAndEachGetsAWordForWhy() {
        List<JournalRecord> records = new ArrayList<JournalRecord>();
        records.add(record(JournalLevel.ERROR, "pnShop", "database locked", started + 30_000));
        records.add(record(JournalLevel.ERROR, "pnAuth", "login failed", now - Duration.ofMinutes(2).toMillis()));
        records.addAll(warnings("pnCaptcha", "slow answer", 6));

        List<String> shown = lines(records);
        String text = String.join("\n", shown);

        assertTrue(any(shown, "pnShop", "старт"), text);
        assertTrue(any(shown, "pnAuth", "только что"), text);
        assertFalse(any(shown, "pnCaptcha", "slow answer"), "only two events are listed: " + text);
        assertTrue(text.contains("2 ошибки"), text);
        assertTrue(text.contains("6 предупреждений"), text);
        assertTrue(text.contains("Проверьте, что плагин подходит к версии сервера."), text);
    }

    @Test
    void aRecurringWarningIsMarkedAndWarningsAloneRaiseNoAlarm() {
        String shown = String.join("\n", lines(warnings("pnCaptcha", "slow answer", 6)));
        assertTrue(shown.contains("pnCaptcha ×6 повтор"), shown);

        String single = String.join("\n", lines(Collections.singletonList(
            record(JournalLevel.WARNING, "pnMap", "slow tile", now - Duration.ofHours(5).toMillis()))));
        assertTrue(single.contains("Ничего срочного"), single);
        assertFalse(single.contains("подробнее"), single);
    }

    @Test
    void aFreshEventThatRepeatsGetsTheAdviceForRepeatingProblemsAndFitsOneChatLine() {
        List<JournalRecord> records = new ArrayList<JournalRecord>();
        for (int i = 1; i <= 16; i++) {
            records.add(record(JournalLevel.ERROR, "pnLibrary",
                "Error occurred while enabling pnLibraryAcceptance v2.2.0-beta.6 (Is it up to date?)", now - i * 30_000L));
        }
        List<String> shown = lines(records);
        String text = String.join("\n", shown);

        assertTrue(text.contains("Покажите карточку автору плагина"), text);
        assertFalse(text.contains("Посмотрите, что делали игроки"), text);
        for (String line : shown) {
            if (line.contains("pnLibrary ×16")) {
                assertTrue(line.length() <= 52, text);
            }
        }
    }

    @Test
    void theStripHasOneColumnPerSliceOfTheWindowAndEndsAtNow() {
        String strip = null;
        for (String line : lines(Collections.singletonList(record(JournalLevel.ERROR, "pnShop", "locked", now - 1_000)))) {
            if (line.endsWith("сейчас")) {
                strip = line;
                break;
            }
        }
        String columns = strip.trim().substring(0, strip.trim().length() - "сейчас".length()).trim();

        assertEquals(24, columns.length());
        assertEquals('▁', columns.charAt(columns.length() - 1));
        int dots = 0;
        for (char symbol : columns.toCharArray()) {
            dots += symbol == '·' ? 1 : 0;
        }
        assertEquals(23, dots);
    }
}
