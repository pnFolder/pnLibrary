package org.pnfolder.pnlibrary.core.commands.events;

import static org.pnfolder.pnlibrary.core.commands.events.BusConstants.DAYS_PER_PAGE;
import static org.pnfolder.pnlibrary.core.commands.events.BusConstants.EVENTS_PER_PAGE;
import static org.pnfolder.pnlibrary.core.commands.events.BusConstants.ROOT;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.events.inspect.EventInspector;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.commands.engine.ChatPager;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** Chat pages with recent bus history. */
final class BusHistoryPages {
    private static final int HOVER_EVENTS = 5;
    private static final Comparator<EventDay> MOST_CALLED = new Comparator<EventDay>() {
        @Override
        public int compare(EventDay left, EventDay right) {
            return Long.compare(right.getCalls(), left.getCalls());
        }
    };

    private final PnLibrary library;

    BusHistoryPages(PnLibrary library) {
        this.library = library;
    }

    private EventInspector bus() {
        return library.getEvents().getInspector();
    }

    /**
     * One line per day, newest first, so a busy server never floods the chat: the day, its calls, how many different
     * events were called and how many calls were denied. A click opens that day; for one event it opens the event.
     */
    void history(CommandContext context, String typed, int requestedPage) {
        ChatKit kit = CommandOutput.kit(context);
        String name = typed == null ? null : BusChat.resolve(bus(), typed);
        List<Map.Entry<LocalDate, List<EventDay>>> days = new ArrayList<Map.Entry<LocalDate, List<EventDay>>>(
                groupByDate(bus().history(name)).entrySet());
        int pages = ChatPager.pageCount(days.size(), DAYS_PER_PAGE);
        int page = ChatPager.clamp(requestedPage, pages);
        List<Map.Entry<LocalDate, List<EventDay>>> shown = ChatPager.slice(days, page, DAYS_PER_PAGE);
        int keep = bus().getHistoryDays();
        String details = keep <= 0 ? "выключена" : "хранится " + keep + " " + RussianText.plural(keep, "день", "дня", "дней");
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header(name == null ? "История вызовов" : "История: " + CoreFormatting.shortName(name),
                kit.text(details, ChatPalette.LABEL)));
        lines.add(kit.blank());
        if (keep <= 0) {
            lines.add(kit.note("История отключена в коде (eventHistoryDays = 0)."));
        } else if (shown.isEmpty()) {
            lines.add(kit.note("За это время вызовов не было."));
        }
        lines.addAll(dayRows(kit, shown, name));
        if (pages > 1) {
            lines.add(kit.blank());
            lines.add(ChatPager.pager(kit, name == null ? ROOT + " history" : ROOT + " history of " + name, page, pages));
        }
        if (name == null && !shown.isEmpty()) {
            lines.add(kit.note("Нажмите на день, чтобы увидеть его события.", ChatPalette.FAINT));
        }
        lines.add(kit.blank());
        lines.addAll(BusChat.footer(kit, BusChat.backToEvents(kit)));
        CommandOutput.send(context, lines);
    }

    private static Map<LocalDate, List<EventDay>> groupByDate(List<EventDay> entries) {
        Map<LocalDate, List<EventDay>> grouped = new LinkedHashMap<LocalDate, List<EventDay>>();
        for (EventDay entry : entries) {
            List<EventDay> day = grouped.get(entry.getDate());
            if (day == null) {
                day = new ArrayList<EventDay>();
                grouped.put(entry.getDate(), day);
            }
            day.add(entry);
        }
        return grouped;
    }

    private static long totalCalls(List<EventDay> entries) {
        long total = 0;
        for (EventDay entry : entries) {
            total += entry.getCalls();
        }
        return total;
    }

    private static long totalDenied(List<EventDay> entries) {
        long total = 0;
        for (EventDay entry : entries) {
            total += entry.getDenied();
        }
        return total;
    }

    private List<Component> dayRows(ChatKit kit, List<Map.Entry<LocalDate, List<EventDay>>> shown, String name) {
        long busiest = 1L;
        boolean any = false;
        for (Map.Entry<LocalDate, List<EventDay>> day : shown) {
            long calls = totalCalls(day.getValue());
            busiest = any ? Math.max(busiest, calls) : calls;
            any = true;
        }
        LocalDate today = LocalDate.now(DisplayZone.getZone());
        List<Component> rows = new ArrayList<Component>();
        for (Map.Entry<LocalDate, List<EventDay>> day : shown) {
            rows.add(dayRow(kit, day.getKey(), today, day.getValue(), busiest, name));
        }
        return rows;
    }

    private Component dayRow(ChatKit kit, LocalDate date, LocalDate today, List<EventDay> entries, long busiest, String name) {
        long total = totalCalls(entries);
        long denied = totalDenied(entries);
        Component line = kit.text("  ").append(kit.text(BusChat.bar(kit, total, busiest), ChatPalette.INFO))
                .append(kit.text(" " + CoreFormatting.dayTitle(date, today), ChatPalette.ACCENT))
                .append(kit.text("  " + total + " " + RussianText.plural(total, "вызов", "вызова", "вызовов"), ChatPalette.TEXT));
        if (name == null) {
            long kinds = entries.size();
            line = line.append(kit.text(" · " + kinds + " " + RussianText.plural(kinds, "событие", "события", "событий"),
                    ChatPalette.SOFT));
        }
        if (denied > 0) {
            line = line.append(kit.text(" · " + denied + " " + RussianText.plural(denied, "отказ", "отказа", "отказов"),
                    ChatPalette.DANGER));
        }
        if (!kit.getReader().isInteractive()) {
            return line;
        }
        String command = name == null ? ROOT + " history day " + date : ROOT + " view " + name;
        return line.hoverEvent(kit.tooltip(dayHover(kit, date, entries, name).toArray(new Component[0])))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private List<Component> dayHover(ChatKit kit, LocalDate date, List<EventDay> entries, String name) {
        List<EventDay> sorted = new ArrayList<EventDay>(entries);
        Collections.sort(sorted, MOST_CALLED);
        List<EventDay> top = sorted.subList(0, Math.min(HOVER_EVENTS, sorted.size()));
        List<Component> hover = new ArrayList<Component>();
        hover.add(kit.text(CoreFormatting.DAY.format(date), ChatPalette.ACCENT));
        hover.add(kit.blank());
        for (EventDay entry : top) {
            hover.add(kit.text(CoreFormatting.shortName(entry.getEventType()) + ": " + entry.getCalls(), ChatPalette.TEXT));
        }
        if (entries.size() > top.size()) {
            hover.add(kit.text("и ещё " + (entries.size() - top.size()), ChatPalette.FAINT));
        }
        hover.add(kit.blank());
        hover.add(kit.text(name == null ? "Нажмите, чтобы открыть день ›" : "Нажмите, чтобы открыть событие ›", ChatPalette.ACCENT));
        return hover;
    }

    void historyDay(CommandContext context, String typed, int requestedPage) {
        ChatKit kit = CommandOutput.kit(context);
        LocalDate date;
        try {
            date = LocalDate.parse(typed);
        } catch (DateTimeParseException invalid) {
            context.getSender().sendMessage(kit.text("Дата должна быть вида 2026-10-27. Список дней: " + ROOT + " history",
                    ChatPalette.DANGER));
            return;
        }
        List<EventDay> entries = new ArrayList<EventDay>();
        for (EventDay entry : bus().history()) {
            if (entry.getDate().equals(date)) {
                entries.add(entry);
            }
        }
        Collections.sort(entries, MOST_CALLED);
        CommandOutput.send(context, dayLines(kit, date, entries, requestedPage));
    }

    private List<Component> dayLines(ChatKit kit, LocalDate date, List<EventDay> entries, int requestedPage) {
        int pages = ChatPager.pageCount(entries.size(), EVENTS_PER_PAGE);
        int page = ChatPager.clamp(requestedPage, pages);
        LocalDate today = LocalDate.now(DisplayZone.getZone());
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header(CoreFormatting.dayTitle(date, today), kit.text(CoreFormatting.DAY.format(date), ChatPalette.LABEL)));
        lines.add(kit.field("Вызовов", kit.text(String.valueOf(totalCalls(entries)), ChatPalette.TEXT)));
        lines.add(kit.field("Разных событий", kit.text(String.valueOf(entries.size()), ChatPalette.TEXT)));
        lines.add(kit.blank());
        if (entries.isEmpty()) {
            lines.add(kit.note("В этот день вызовов не было."));
        }
        long busiest = entries.isEmpty() ? 1L : entries.get(0).getCalls();
        for (EventDay entry : ChatPager.slice(entries, page, EVENTS_PER_PAGE)) {
            lines.add(historyRow(kit, entry, busiest));
        }
        if (pages > 1) {
            lines.add(kit.blank());
            lines.add(ChatPager.pager(kit, ROOT + " history day " + date, page, pages));
        }
        lines.add(kit.blank());
        lines.addAll(BusChat.footer(kit, kit.button("назад", ROOT + " history", "К списку дней", ChatPalette.ACCENT)));
        return lines;
    }

    private Component historyRow(ChatKit kit, EventDay day, long busiest) {
        Component line = kit.text("    ").append(kit.text(BusChat.bar(kit, day.getCalls(), busiest), ChatPalette.INFO))
                .append(kit.text(" " + day.getCalls(), ChatPalette.TEXT))
                .append(kit.text("  " + CoreFormatting.shortName(day.getEventType()), ChatPalette.ACCENT));
        if (day.getDenied() > 0) {
            line = line.append(kit.text("  отказов " + day.getDenied(), ChatPalette.DANGER));
        }
        if (!kit.getReader().isInteractive()) {
            return line;
        }
        return line.hoverEvent(kit.tooltip(
                kit.text(CoreFormatting.shortName(day.getEventType()), ChatPalette.ACCENT),
                kit.text(day.getEventType(), ChatPalette.LABEL),
                kit.blank(),
                kit.hoverField("Вызовов", kit.text(String.valueOf(day.getCalls()), ChatPalette.TEXT)),
                kit.hoverField("Отказов", kit.text(String.valueOf(day.getDenied()),
                        day.getDenied() > 0 ? ChatPalette.DANGER : ChatPalette.TEXT)),
                kit.blank(),
                kit.text("Нажмите, чтобы открыть событие ›", ChatPalette.ACCENT)))
                .clickEvent(ClickEvent.runCommand(ROOT + " view " + day.getEventType()));
    }
}
