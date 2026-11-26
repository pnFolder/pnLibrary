package org.pnfolder.pnlibrary.core.commands.events;

import static org.pnfolder.pnlibrary.core.commands.events.BusConstants.PAGE_SIZE;
import static org.pnfolder.pnlibrary.core.commands.events.BusConstants.ROOT;
import static org.pnfolder.pnlibrary.core.commands.events.BusConstants.TOP_SIZE;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;
import net.kyori.adventure.text.Component;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.events.inspect.EventInspector;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventHour;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.commands.engine.ChatPager;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** Chat pages describing bus listeners. */
final class BusListenerPages {
    private final PnLibrary library;

    BusListenerPages(PnLibrary library) {
        this.library = library;
    }

    private EventInspector bus() {
        return library.getEvents().getInspector();
    }

    void listeners(CommandContext context, int requestedPage) {
        ChatKit kit = CommandOutput.kit(context);
        List<ListenerInfo> all = bus().listeners();
        int pages = ChatPager.pageCount(all.size(), PAGE_SIZE);
        int page = ChatPager.clamp(requestedPage, pages);
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header("Все слушатели", kit.text(String.valueOf(all.size()), ChatPalette.LABEL)));
        lines.add(kit.blank());
        if (all.isEmpty()) {
            lines.add(kit.note("Слушателей нет."));
        }
        for (ListenerInfo info : ChatPager.slice(all, page, PAGE_SIZE)) {
            lines.add(BusChat.listenerRow(kit, info));
        }
        closePage(kit, lines, ROOT + " listeners", page, pages);
        CommandOutput.send(context, lines);
    }

    private void closePage(ChatKit kit, List<Component> lines, String base, int page, int pages) {
        if (pages > 1) {
            lines.add(kit.blank());
            lines.add(ChatPager.pager(kit, base, page, pages));
        }
        lines.add(kit.blank());
        lines.addAll(BusChat.footer(kit, BusChat.backToEvents(kit)));
    }

    void slow(CommandContext context, int requestedPage) {
        List<ListenerInfo> ran = new ArrayList<ListenerInfo>();
        for (ListenerInfo info : bus().listeners()) {
            if (info.getInvocations() > 0) {
                ran.add(info);
            }
        }
        Collections.sort(ran, new Comparator<ListenerInfo>() {
            @Override
            public int compare(ListenerInfo left, ListenerInfo right) {
                return Long.compare(right.getAverageNanos(), left.getAverageNanos());
            }
        });
        listenerPage(context, requestedPage, ran, "Медленные слушатели", "Слушатели ещё не запускались.", ROOT + " slow");
    }

    void errors(CommandContext context, int requestedPage) {
        List<ListenerInfo> failed = new ArrayList<ListenerInfo>();
        for (ListenerInfo info : bus().listeners()) {
            if (info.getFailures() > 0) {
                failed.add(info);
            }
        }
        Collections.sort(failed, new Comparator<ListenerInfo>() {
            @Override
            public int compare(ListenerInfo left, ListenerInfo right) {
                return Long.compare(right.getFailures(), left.getFailures());
            }
        });
        listenerPage(context, requestedPage, failed, "Слушатели с ошибками", "Ошибок не было.", ROOT + " errors");
    }

    void topCalls(CommandContext context) {
        top(context, "Чаще всего вызывались", "Событий ещё не было.", EventInfo::getCalls);
    }

    void topDenied(CommandContext context) {
        top(context, "Чаще всего отклонялись", "Отказов не было.", EventInfo::getDenied);
    }

    private void top(CommandContext context, String title, String empty, final ToLongFunction<EventInfo> count) {
        ChatKit kit = CommandOutput.kit(context);
        List<EventInfo> ranked = new ArrayList<EventInfo>();
        for (EventInfo info : bus().events()) {
            if (count.applyAsLong(info) > 0) {
                ranked.add(info);
            }
        }
        Collections.sort(ranked, new Comparator<EventInfo>() {
            @Override
            public int compare(EventInfo left, EventInfo right) {
                return Long.compare(count.applyAsLong(right), count.applyAsLong(left));
            }
        });
        ranked = ranked.subList(0, Math.min(TOP_SIZE, ranked.size()));
        long busiest = ranked.isEmpty() ? 0 : count.applyAsLong(ranked.get(0));
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header(title, kit.text(String.valueOf(ranked.size()), ChatPalette.LABEL)));
        lines.add(kit.blank());
        if (ranked.isEmpty()) {
            lines.add(kit.note(empty));
        }
        for (EventInfo info : ranked) {
            lines.add(kit.text("   ").append(kit.text(BusChat.bar(kit, count.applyAsLong(info), busiest), ChatPalette.SOFT))
                    .append(kit.text(" " + count.applyAsLong(info) + "  ", ChatPalette.LABEL))
                    .append(kit.text(CoreFormatting.shortName(info.getEventType()), ChatPalette.TEXT)));
        }
        lines.add(kit.blank());
        lines.addAll(BusChat.footer(kit, BusChat.backToEvents(kit)));
        CommandOutput.send(context, lines);
    }

    void range(CommandContext context, String word) {
        ChatKit kit = CommandOutput.kit(context);
        int hours = BusConstants.RANGES.get(word);
        LocalDateTime since = LocalDateTime.now(DisplayZone.getZone()).truncatedTo(ChronoUnit.HOURS).minusHours(hours - 1L);
        Map<String, long[]> totals = callsSince(since);
        List<Map.Entry<String, long[]>> ranked = new ArrayList<Map.Entry<String, long[]>>(totals.entrySet());
        Collections.sort(ranked, new Comparator<Map.Entry<String, long[]>>() {
            @Override
            public int compare(Map.Entry<String, long[]> left, Map.Entry<String, long[]> right) {
                return Long.compare(right.getValue()[0], left.getValue()[0]);
            }
        });
        ranked = ranked.subList(0, Math.min(TOP_SIZE, ranked.size()));
        long all = 0;
        for (long[] sums : totals.values()) {
            all += sums[0];
        }
        CommandOutput.send(context, rangeLines(kit, word, hours, all, ranked));
    }

    private List<Component> rangeLines(ChatKit kit, String word, int hours, long all, List<Map.Entry<String, long[]>> ranked) {
        long busiest = ranked.isEmpty() ? 0 : ranked.get(0).getValue()[0];
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header("Вызовы за " + word, kit.text(String.valueOf(all), ChatPalette.LABEL)));
        lines.add(kit.blank());
        if (bus().getHistoryDays() <= 0) {
            lines.add(kit.note("История выключена."));
        } else if (ranked.isEmpty()) {
            lines.add(kit.note("За этот период событий не было."));
        }
        for (Map.Entry<String, long[]> entry : ranked) {
            long calls = entry.getValue()[0];
            Component line = kit.text("   ").append(kit.text(BusChat.bar(kit, calls, busiest), ChatPalette.SOFT))
                    .append(kit.text(" " + calls + "  ", ChatPalette.LABEL))
                    .append(kit.text(CoreFormatting.shortName(entry.getKey()), ChatPalette.TEXT))
                    .append(kit.text("  " + String.format(Locale.getDefault(), "%.1f", (double) calls / hours) + "/ч",
                            ChatPalette.FAINT));
            if (entry.getValue()[1] > 0) {
                line = line.append(kit.text("  отказов " + entry.getValue()[1], ChatPalette.DANGER));
            }
            lines.add(line);
        }
        lines.add(kit.blank());
        lines.addAll(BusChat.footer(kit, BusChat.backToEvents(kit)));
        return lines;
    }

    /** Calls and denials per event since {@code since}, as two-element arrays. */
    private Map<String, long[]> callsSince(LocalDateTime since) {
        Map<String, long[]> totals = new HashMap<String, long[]>();
        for (EventHour hour : bus().hourly()) {
            if (hour.getHour().isBefore(since)) {
                continue;
            }
            long[] sums = totals.get(hour.getEventType());
            if (sums == null) {
                sums = new long[2];
                totals.put(hour.getEventType(), sums);
            }
            sums[0] += hour.getCalls();
            sums[1] += hour.getDenied();
        }
        return totals;
    }

    void exportCsv(CommandContext context) {
        export(context, bus().exportHistoryCsv());
    }

    void exportJson(CommandContext context) {
        export(context, bus().exportHistoryJson());
    }

    private void export(CommandContext context, Path file) {
        context.getSender().sendMessage(file == null
                ? Component.text("Выгрузка недоступна: история выключена или файл не записать.", ChatPalette.DANGER)
                : Component.text("Счётчики записаны в " + file, ChatPalette.OK));
    }

    void pluginView(CommandContext context, String id, int requestedPage) {
        List<ListenerInfo> all = new ArrayList<ListenerInfo>();
        for (ListenerInfo info : bus().listeners()) {
            if (info.getPlugin().toString().equalsIgnoreCase(id)) {
                all.add(info);
            }
        }
        Collections.sort(all, new Comparator<ListenerInfo>() {
            @Override
            public int compare(ListenerInfo left, ListenerInfo right) {
                return Long.compare(right.getTotalNanos(), left.getTotalNanos());
            }
        });
        listenerPage(context, requestedPage, all, "Плагин " + id, "У плагина нет слушателей.", ROOT + " plugin " + id);
    }

    private void listenerPage(CommandContext context, int requestedPage, List<ListenerInfo> all, String title, String empty,
            String base) {
        ChatKit kit = CommandOutput.kit(context);
        int pages = ChatPager.pageCount(all.size(), PAGE_SIZE);
        int page = ChatPager.clamp(requestedPage, pages);
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header(title, kit.text(String.valueOf(all.size()), ChatPalette.LABEL)));
        lines.add(kit.blank());
        if (all.isEmpty()) {
            lines.add(kit.note(empty));
        }
        for (ListenerInfo info : ChatPager.slice(all, page, PAGE_SIZE)) {
            lines.add(timedRow(kit, info));
        }
        closePage(kit, lines, base, page, pages);
        CommandOutput.send(context, lines);
    }

    private Component timedRow(ChatKit kit, ListenerInfo info) {
        Component line = kit.text("   ").append(kit.text(BusChat.ownerLabel(info), ChatPalette.ACCENT)).append(kit.dot())
                .append(kit.text(CoreFormatting.shortName(info.getEventType()), ChatPalette.TEXT))
                .append(kit.text("  средн. " + CoreFormatting.nanosText(info.getAverageNanos()), ChatPalette.LABEL))
                .append(kit.text("  макс. " + CoreFormatting.nanosText(info.getMaxNanos()), ChatPalette.SOFT))
                .append(kit.text("  запусков " + info.getInvocations(), ChatPalette.FAINT));
        if (info.getFailures() > 0) {
            line = line.append(kit.text("  ошибок " + info.getFailures(), ChatPalette.DANGER));
        }
        return line;
    }
}
