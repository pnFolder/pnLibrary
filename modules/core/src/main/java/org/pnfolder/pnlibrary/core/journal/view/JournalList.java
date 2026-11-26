package org.pnfolder.pnlibrary.core.journal.view;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** The journal as pages of events with repeats folded into one row. */
final class JournalList {
    private JournalList() {
    }

    /** Prints one page of the journal for the filter and page named in {@code arguments}, or tells which level words exist. */
    static void show(CommandSender sender, ChatKit kit, JournalStore journal, List<String> arguments, String root) {
        String word = arguments.isEmpty() ? "all" : arguments.get(0).toLowerCase(Locale.ROOT);
        JournalFilter filter = JournalFilter.find(word);
        if (filter == null) {
            sender.sendMessage(kit.text("Неизвестный уровень " + word + ". Доступны: all, error, warning, info.",
                ChatPalette.DANGER));
            return;
        }
        List<List<JournalRecord>> everything = foldRepeats(journal.read(JournalConstants.SCAN_LIMIT));
        List<List<JournalRecord>> groups = new ArrayList<>();
        for (List<JournalRecord> group : everything) {
            if (filter.shows(group)) {
                groups.add(group);
            }
        }
        int pages = Math.max((groups.size() + JournalConstants.PAGE_SIZE - 1) / JournalConstants.PAGE_SIZE, 1);
        int page = Math.min(Math.max(pageNumber(arguments), 1), pages);
        int from = Math.min((page - 1) * JournalConstants.PAGE_SIZE, groups.size());
        List<List<JournalRecord>> rows = groups.subList(from, Math.min(groups.size(),
            from + JournalConstants.PAGE_SIZE));

        List<Component> lines = new ArrayList<>(head(kit, groups.size(),
            tabs(kit, everything, filter.word, root)));
        if (rows.isEmpty()) {
            lines.add(kit.note("Здесь пока пусто."));
        }
        for (List<JournalRecord> group : rows) {
            lines.add(JournalRows.row(kit, group, AudienceExtensions.isConsole(sender), root));
            lines.add(kit.blank());
        }
        if (pages > 1) {
            lines.add(pager(kit, filter.word, page, pages, root));
        }
        if (!kit.getReader().isInteractive()) {
            lines.add(kit.blank());
            lines.add(kit.note("Уровни: " + root + " all | error | warning | info [страница]; карточка: " + root
                + " view <id>"));
        }
        for (Component line : lines) {
            sender.sendMessage(line);
        }
    }

    private static int pageNumber(List<String> arguments) {
        if (arguments.size() < 2) {
            return 1;
        }
        try {
            return Integer.parseInt(arguments.get(1));
        } catch (NumberFormatException notANumber) {
            return 1;
        }
    }

    /** Equal events merged into one list each, newest first, and the lists ordered by their newest record. */
    private static List<List<JournalRecord>> foldRepeats(List<JournalRecord> records) {
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

    private static List<Component> head(ChatKit kit, int count, Component tabs) {
        String records = count + " " + RussianText.plural(count, "запись", "записи", "записей");
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.blank());
        lines.add(kit.header("События", kit.text(records, ChatPalette.LABEL)));
        lines.add(tabs);
        lines.add(kit.blank());
        return lines;
    }

    /**
     * {@code Показать:} and one button per filter, such as {@code [ошибки 3]}. Every button has the same gray
     * brackets; the open filter is violet and the others are gray, so the difference is one color and never depends
     * on the level.
     */
    private static Component tabs(ChatKit kit, List<List<JournalRecord>> everything, String current, String root) {
        Component line = kit.text(" Показать: ", ChatPalette.LABEL);
        boolean first = true;
        for (JournalFilter filter : JournalFilter.ALL) {
            if (!first) {
                line = line.append(kit.text(" "));
            }
            first = false;
            int count = 0;
            for (List<JournalRecord> group : everything) {
                if (filter.shows(group)) {
                    count++;
                }
            }
            line = line.append(tab(kit, filter, count, filter.word.equals(current), root));
        }
        return line;
    }

    private static Component tab(ChatKit kit, JournalFilter filter, int count, boolean open, String root) {
        net.kyori.adventure.text.format.TextColor body = open ? ChatPalette.ACCENT
            : count == 0 ? ChatPalette.FAINT : ChatPalette.SOFT;
        Component button = kit.text("[", ChatPalette.FAINT).append(kit.text(filter.label + " " + count, body))
            .append(kit.text("]", ChatPalette.FAINT));
        if (open || !kit.getReader().isInteractive()) {
            return button;
        }
        return button.clickEvent(ClickEvent.runCommand(root + " " + filter.word)).hoverEvent(kit.tooltip(
            kit.text("Нажмите, чтобы показать: " + filter.label, ChatPalette.ACCENT), kit.blank(),
            kit.text(root + " " + filter.word, ChatPalette.LABEL)));
    }

    private static Component link(ChatKit kit, String word, int pages, String root, int target, String label) {
        return kit.option(label, root + " " + word + " " + target, "Страница " + target + " из " + pages,
            ChatPalette.SOFT);
    }

    /** {@code « 1 … 4 [5] 6 … 12 »}: arrows and a window of page numbers, so a long journal is never one wall of rows. */
    private static Component pager(@NotNull ChatKit kit, String word, int page, int pages, String root) {
        TreeSet<Integer> shown = new TreeSet<>();
        for (int number : new int[] {1, pages, page - 1, page, page + 1}) {
            if (number >= 1 && number <= pages) {
                shown.add(number);
            }
        }
        Component line = kit.text("  ").append(page > 1
            ? link(kit, word, pages, root, page - 1, "‹ назад") : kit.text("[‹ назад]", ChatPalette.FAINT));
        int previous = 0;
        for (int number : shown) {
            if (number - previous > 1) {
                line = line.append(kit.text(" …", ChatPalette.FAINT));
            }
            line = line.append(kit.text(" ")).append(number == page
                ? kit.text(String.valueOf(number), ChatPalette.ACCENT).decorate(TextDecoration.BOLD)
                : link(kit, word, pages, root, number, String.valueOf(number)));
            previous = number;
        }
        line = line.append(kit.text(" ")).append(page < pages
            ? link(kit, word, pages, root, page + 1, "дальше ›") : kit.text("[дальше ›]", ChatPalette.FAINT));
        return line.append(kit.text("   страница " + page + " из " + pages, ChatPalette.FAINT));
    }
}
