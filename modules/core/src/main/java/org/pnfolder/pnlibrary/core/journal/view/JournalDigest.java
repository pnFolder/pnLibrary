package org.pnfolder.pnlibrary.core.journal.view;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The short notice an administrator sees when joining: how many problems there were, when they happened (a strip of
 * the window), the one or two events worth a look with a word for why, one piece of advice and where to click.
 */
public final class JournalDigest {
    private static final String DEFAULT_ROOT = "/pn events";

    /** Notable events listed in the notice; the rest are in the journal. */
    private static final int MAX_NOTABLE = 2;

    private JournalDigest() {
    }

    /** The notice for the default {@code /pn events} command. */
    public static @NotNull List<Component> join(@NotNull List<JournalRecord> records, long now, int windowHours,
                                                long startedAtMillis) {
        return join(records, now, windowHours, startedAtMillis, DEFAULT_ROOT);
    }

    /**
     * Builds the notice; returns no lines when nothing needs attention, so a healthy server stays quiet.
     *
     * @param records warnings and errors of the window, any order
     * @param now current time in epoch milliseconds
     * @param windowHours length of the window, shown in the header
     * @param startedAtMillis when the library started, to recognize startup problems
     * @param root the command the buttons run
     */
    public static @NotNull List<Component> join(@NotNull List<JournalRecord> records, long now, int windowHours,
                                                long startedAtMillis, @NotNull String root) {
        ChatKit kit = new ChatKit(new ChatReader());
        List<JournalRecord> relevant = new ArrayList<>();
        for (JournalRecord record : records) {
            if (record.getLevel() != JournalLevel.INFO) {
                relevant.add(record);
            }
        }
        List<List<JournalRecord>> groups = newestFirst(relevant);
        if (groups.isEmpty()) {
            return Collections.emptyList();
        }
        boolean hasErrors = false;
        for (List<JournalRecord> group : groups) {
            hasErrors |= group.get(0).getLevel() == JournalLevel.ERROR;
        }
        List<List<JournalRecord>> ranked = rankByUrgency(groups, now, startedAtMillis);
        List<JournalRecord> top = ranked.get(0);
        DigestReason topReason = DigestReason.of(top, now, startedAtMillis);
        int errorCount = 0;
        for (JournalRecord record : relevant) {
            errorCount += record.getLevel() == JournalLevel.ERROR ? 1 : 0;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.header("Журнал", kit.text("за последние " + windowHours + " "
            + RussianText.plural(windowHours, "час", "часа", "часов"), ChatPalette.LABEL)));
        lines.add(summaryLine(kit, errorCount, relevant.size() - errorCount));
        lines.add(new DigestStrip(relevant, now, windowHours, startedAtMillis).line(kit));
        for (List<JournalRecord> group : ranked.subList(0, Math.min(MAX_NOTABLE, ranked.size()))) {
            lines.add(DigestNotable.line(kit, group, DigestReason.of(group, now, startedAtMillis), root));
        }
        lines.addAll(adviceLine(kit, top, topReason, hasErrors));
        lines.add(kit.blank());
        lines.add(actions(kit, root, hasErrors, topReason != DigestReason.PLAIN, top));
        return lines;
    }

    /** Equal events merged into one list each, newest first, and the lists ordered by their newest record. */
    private static List<List<JournalRecord>> newestFirst(List<JournalRecord> records) {
        Map<String, List<JournalRecord>> byId = new LinkedHashMap<>();
        for (JournalRecord record : records) {
            List<JournalRecord> group = byId.get(record.getFingerprint());
            if (group == null) {
                group = new ArrayList<>();
                byId.put(record.getFingerprint(), group);
            }
            group.add(record);
        }
        List<List<JournalRecord>> groups = new ArrayList<>(byId.values());
        for (List<JournalRecord> group : groups) {
            group.sort((left, right) -> Long.compare(right.getTimeMillis(), left.getTimeMillis()));
        }
        groups.sort((left, right) -> Long.compare(right.get(0).getTimeMillis(), left.get(0).getTimeMillis()));
        return groups;
    }

    /** The buttons under the digest; the card button appears only when something is worth a closer look. */
    private static Component actions(ChatKit kit, String root, boolean hasErrors, boolean urgentReason,
                                     List<JournalRecord> top) {
        boolean urgent = hasErrors || urgentReason;
        Component open = kit.button("открыть журнал", hasErrors ? root + " error" : root + " warning",
            "Список событий", ChatPalette.ACCENT);
        Component more = urgent ? kit.button("подробнее", root + " view " + top.get(0).getFingerprint(),
            "Карточка главного события", ChatPalette.ACCENT) : null;
        Component actions = kit.actions(open, more);
        return actions != null ? actions : kit.blank();
    }

    /** Most urgent first; errors before warnings inside one reason. */
    private static List<List<JournalRecord>> rankByUrgency(List<List<JournalRecord>> groups, long now,
                                                           long startedAtMillis) {
        List<List<JournalRecord>> ranked = new ArrayList<>(groups);
        ranked.sort((left, right) -> {
            int byReason = DigestReason.of(left, now, startedAtMillis)
                .compareTo(DigestReason.of(right, now, startedAtMillis));
            return byReason != 0 ? byReason : Integer.compare(levelRank(left), levelRank(right));
        });
        return ranked;
    }

    private static int levelRank(List<JournalRecord> group) {
        return group.get(0).getLevel() == JournalLevel.ERROR ? 0 : 1;
    }

    /** One piece of advice for the top event, or the calm note when nothing is urgent; nothing when only errors stand out plainly. */
    private static List<Component> adviceLine(ChatKit kit, List<JournalRecord> top, DigestReason topReason,
                                              boolean hasErrors) {
        if (topReason == DigestReason.PLAIN) {
            return hasErrors ? Collections.emptyList() : Collections.singletonList(
                kit.note("Ничего срочного. Загляните в журнал, когда будет время.", ChatPalette.FAINT));
        }
        // An event that keeps repeating is a repeating problem even when its last repeat was a moment ago.
        DigestReason reason = topReason == DigestReason.JUST_NOW && top.size() >= DigestReason.RECURRING_FROM
            ? DigestReason.RECURRING : topReason;
        return Collections.singletonList(kit.note(reason.advice, ChatPalette.FAINT));
    }

    /** {@code   ● 3 ошибки   ▲ 2 предупреждения}: only the kinds that occurred. */
    private static Component summaryLine(ChatKit kit, int errorCount, int warningCount) {
        List<Component> summary = new ArrayList<>();
        if (errorCount > 0) {
            summary.add(kit.text(kit.glyph('●') + " " + errorCount + " ", ChatPalette.DANGER)
                .append(kit.text(RussianText.plural(errorCount, "ошибка", "ошибки", "ошибок"), ChatPalette.SOFT)));
        }
        if (warningCount > 0) {
            summary.add(kit.text(kit.glyph('▲') + " " + warningCount + " ", ChatPalette.WARN)
                .append(kit.text(RussianText.plural(warningCount, "предупреждение", "предупреждения",
                    "предупреждений"), ChatPalette.SOFT)));
        }
        Component line = kit.text("  ").append(summary.get(0));
        for (Component part : summary.subList(1, summary.size())) {
            line = line.append(kit.text("   ")).append(part);
        }
        return line;
    }
}
