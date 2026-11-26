package org.pnfolder.pnlibrary.core.journal.view;

import net.kyori.adventure.text.format.TextColor;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

import java.time.Duration;
import java.util.List;

/** Why an event is worth a look; the notice leads with the most urgent one. Order is urgency. */
enum DigestReason {
    STARTUP("старт", "Проверьте, что плагин подходит к версии сервера.", ChatPalette.ORANGE),
    JUST_NOW("только что", "Посмотрите, что делали игроки в это время.", ChatPalette.WARN),
    RECURRING("повтор", "Покажите карточку автору плагина, там есть отчёт.", ChatPalette.INFO),
    PLAIN("", "", ChatPalette.INFO);

    /** A group is "just now" when its last record is this recent. */
    private static final Duration JUST_NOW_WINDOW = Duration.ofMinutes(10);

    /** Records that appear this soon after the library started are reported as startup problems. */
    private static final Duration STARTUP_WINDOW = Duration.ofMinutes(3);

    /** From this many repeats within the window an event is called recurring. */
    static final int RECURRING_FROM = 5;

    final String word;
    final String advice;
    final TextColor color;

    DigestReason(String word, String advice, TextColor color) {
        this.word = word;
        this.advice = advice;
        this.color = color;
    }

    /** Why {@code group}, newest first, deserves a look: it began at startup, just happened, or keeps coming back. */
    static DigestReason of(List<JournalRecord> group, long now, long startedAtMillis) {
        long first = group.get(group.size() - 1).getTimeMillis();
        if (first >= startedAtMillis - 1_000 && first <= startedAtMillis + STARTUP_WINDOW.toMillis()) {
            return STARTUP;
        }
        if (group.get(0).getTimeMillis() >= now - JUST_NOW_WINDOW.toMillis()) {
            return JUST_NOW;
        }
        return group.size() >= RECURRING_FROM ? RECURRING : PLAIN;
    }
}
