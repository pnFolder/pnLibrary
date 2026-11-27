package org.pnfolder.pnlibrary.core.journal.view;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** The detail card of one event, or the timeline of its occurrences. */
final class JournalCard {
    private JournalCard() {
    }

    /** Prints the card of event {@code id}; with {@code repeats} set it shows the timeline instead of the details. */
    static void show(CommandSender sender, ChatKit kit, JournalStore journal, @Nullable String id, boolean repeats,
                     boolean fullTrace, String root) {
        List<JournalRecord> group = new ArrayList<>();
        for (JournalRecord record : journal.read(JournalConstants.SCAN_LIMIT)) {
            if (record.getFingerprint().equals(id)) {
                group.add(record);
            }
        }
        group.sort((left, right) -> Long.compare(right.getTimeMillis(), left.getTimeMillis()));
        if (id == null || group.isEmpty()) {
            sender.sendMessage(kit.text("Такой записи нет: возможно, её уже удалили по сроку хранения.",
                ChatPalette.DANGER));
            return;
        }
        JournalRecord record = group.get(0);
        List<Component> lines = new ArrayList<>(head(kit, id, record));
        lines.addAll(whenSection(kit, group));
        lines.add(kit.blank());
        boolean traceHidden = false;
        if (repeats) {
            lines.addAll(JournalRepeats.section(kit, group));
        } else {
            traceHidden = addDetails(lines, kit, record, AudienceExtensions.isConsole(sender) || fullTrace);
        }
        Component actions = kit.actions(buttons(kit, root, id, repeats, fullTrace, traceHidden, group)
            .toArray(new Component[0]));
        lines.add(actions != null ? actions : kit.blank());
        for (Component line : lines) {
            sender.sendMessage(line);
        }
    }

    /** The title of the card: the event id and level, its source and type. */
    private static List<Component> head(ChatKit kit, String id, JournalRecord record) {
        return Arrays.asList(
            kit.blank(),
            kit.blank(),
            kit.header("Событие " + id, kit.bracketed(JournalRows.levelTag(record.getLevel()),
                JournalRows.levelColor(record.getLevel()))),
            kit.text("  " + record.getSource(), ChatPalette.ACCENT).append(kit.dot())
                .append(kit.text(record.getType(), ChatPalette.LABEL)),
            kit.blank());
    }

    /** The first and the last time of the event and how often it repeated. */
    private static List<Component> whenSection(ChatKit kit, List<JournalRecord> group) {
        long last = group.get(0).getTimeMillis();
        long first = group.get(group.size() - 1).getTimeMillis();
        List<Component> lines = new ArrayList<>();
        lines.add(kit.section("Когда"));
        lines.add(kit.field("Последний раз", JournalTime.moment(kit, last)
            .append(kit.text("  " + JournalTime.ago(last), JournalTime.agoColor(last)))));
        lines.add(kit.field("Часовой пояс", kit.text(JournalTime.zoneLabel(), ChatPalette.SOFT)));
        if (group.size() > 1) {
            long gap = (last - first) / (group.size() - 1);
            lines.add(kit.field("Первый раз", JournalTime.moment(kit, first)
                .append(kit.text("  " + JournalTime.ago(first), JournalTime.agoColor(first)))));
            lines.add(kit.field("Повторов", kit.text(String.valueOf(group.size()), ChatPalette.ORANGE)
                .append(kit.text("  примерно раз в " + JournalTime.span(gap), ChatPalette.SOFT))));
        }
        return lines;
    }

    /** Adds the message, its details and the stack trace; returns whether some trace lines were folded away. */
    private static boolean addDetails(List<Component> lines, ChatKit kit, JournalRecord record, boolean wholeTrace) {
        lines.add(kit.section("Что случилось"));
        for (String line : CoreFormatting.wrapText(record.getMessage(), JournalConstants.CARD_WIDTH)) {
            lines.add(kit.text("    " + line, ChatPalette.TEXT));
        }
        for (Map.Entry<String, String> detail : record.getDetails().entrySet()) {
            if (!JournalRecord.STACKTRACE.equals(detail.getKey())) {
                lines.add(kit.text("    " + detail.getKey() + ": ", ChatPalette.LABEL)
                    .append(kit.text(detail.getValue(), ChatPalette.SOFT)));
            }
        }
        boolean hidden = false;
        String trace = record.getDetails().get(JournalRecord.STACKTRACE);
        if (trace != null) {
            lines.add(kit.blank());
            lines.add(kit.section("Стек вызовов"));
            TraceView view = JournalTrace.traceView(kit, Arrays.asList(trace.split("\r\n|\n|\r", -1)), wholeTrace);
            lines.addAll(view.getLines());
            hidden = view.isTrimmed();
        }
        lines.add(kit.blank());
        return hidden;
    }

    /** The buttons under the card; a null entry is a button that does not apply. */
    private static List<Component> buttons(ChatKit kit, String root, String id, boolean repeats, boolean fullTrace,
                                           boolean traceHidden, List<JournalRecord> group) {
        List<Component> buttons = new ArrayList<>();
        buttons.add(kit.button("назад", root, "К списку событий", ChatPalette.ACCENT));
        if (repeats) {
            buttons.add(kit.button("карточка", root + " view " + id, "Вернуться к карточке", ChatPalette.ACCENT));
        } else if (group.size() > 1) {
            buttons.add(kit.button("повторы", root + " times " + id, "Когда это повторялось", ChatPalette.ACCENT));
        } else {
            buttons.add(null);
        }
        if (traceHidden) {
            buttons.add(kit.button("показать всё", root + " full " + id, "Стек вызовов целиком, ничего не свёрнуто",
                ChatPalette.ACCENT));
        } else if (fullTrace) {
            buttons.add(kit.button("свернуть", root + " view " + id, "Снова свернуть служебные вызовы",
                ChatPalette.ACCENT));
        } else {
            buttons.add(null);
        }
        if (kit.getReader().isInteractive()) {
            buttons.add(copyButton(kit, group));
        }
        return buttons;
    }

    /** Copies the report for the plugin author; only in a chat that can click. */
    private static Component copyButton(ChatKit kit, List<JournalRecord> group) {
        return kit.bracketed("скопировать", ChatPalette.OK)
            .clickEvent(ClickEvent.copyToClipboard(clipboardText(group.get(0), group.size())))
            .hoverEvent(kit.tooltip(
                kit.text("Скопировать отчёт для автора плагина", ChatPalette.OK),
                kit.blank(),
                kit.text("Время, число повторов, сообщение и весь стек вызовов", ChatPalette.LABEL)));
    }

    /** Plain text for the plugin author: what happened, how often, and the whole stack trace. */
    static @NotNull String clipboardText(@NotNull JournalRecord record, int count) {
        StringBuilder text = new StringBuilder();
        text.append('[').append(record.getLevel()).append("] ").append(record.getSource()).append(" · ")
            .append(record.getType()).append('\n');
        text.append("Время: ").append(JournalTime.stamp(record.getTimeMillis())).append(' ')
            .append(JournalTime.zoneLabel()).append(", повторов: ").append(count).append('\n');
        text.append(record.getMessage()).append('\n');
        for (Map.Entry<String, String> detail : record.getDetails().entrySet()) {
            if (!JournalRecord.STACKTRACE.equals(detail.getKey())) {
                text.append(detail.getKey()).append(": ").append(detail.getValue()).append('\n');
            }
        }
        String trace = record.getDetails().get(JournalRecord.STACKTRACE);
        if (trace != null) {
            text.append('\n').append(trace);
        }
        return text.toString().trim();
    }
}
