package org.pnfolder.pnlibrary.core.stats;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.common.lang.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.journal.view.JournalTime;

/** The chat lines of the statistics card; also used by the menu. */
public final class StatsLines {
    private StatsLines() {
    }

    public static @NotNull List<Component> of(@NotNull StatsSnapshot now, @NotNull List<StatsSnapshot> history) {
        List<Component> lines = new ArrayList<Component>();
        lines.add(Component.text("Статистика pnLibrary", ChatPalette.ACCENT));
        lines.add(row("Задачи", now.getTasksActive() + " активно, " + now.getTasksFailed() + " с ошибкой",
            now.getTasksFailed() > 0 ? ChatPalette.WARN : ChatPalette.OK));
        lines.add(row("Запуски задач", now.getTaskRuns() + ", пропущено " + now.getTaskSkipped(), ChatPalette.SOFT));
        lines.add(row("События", now.getEventsCalled() + " вызовов, отменено " + now.getEventsDenied(), ChatPalette.SOFT));
        lines.add(row("За сутки", now.getErrors24h() + " ошибок, " + now.getWarnings24h() + " предупреждений",
            now.getErrors24h() > 0 ? ChatPalette.DANGER : ChatPalette.OK));
        if (!now.getErrorSources().isEmpty()) {
            lines.add(row("Больше всего ошибок", sources(now.getErrorSources()), ChatPalette.WARN));
        }
        if (!history.isEmpty()) {
            lines.add(Component.text("Прошлые срезы", ChatPalette.LABEL));
            ZoneId zone = DisplayZone.getZone();
            for (StatsSnapshot past : history) {
                String when = JournalTime.ago(past.getTimeMillis(), System.currentTimeMillis(), zone);
                lines.add(row(when, past.getTasksActive() + " задач, " + past.getErrors24h() + " ошибок", ChatPalette.FAINT));
            }
        }
        return lines;
    }

    private static String sources(List<Pair<String, Integer>> sources) {
        StringBuilder text = new StringBuilder();
        for (Pair<String, Integer> source : sources) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(source.getFirst()).append(" (").append(source.getSecond()).append(')');
        }
        return text.toString();
    }

    private static Component row(String label, String value, TextColor color) {
        return Component.text(label + ": ", ChatPalette.SOFT).append(Component.text(value, color));
    }
}
