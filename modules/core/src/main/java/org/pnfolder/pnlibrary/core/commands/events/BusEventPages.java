package org.pnfolder.pnlibrary.core.commands.events;

import static org.pnfolder.pnlibrary.core.commands.events.BusConstants.PAGE_SIZE;
import static org.pnfolder.pnlibrary.core.commands.events.BusConstants.ROOT;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.events.inspect.EventInspector;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.core.commands.engine.ChatPager;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** Chat pages describing bus events. */
final class BusEventPages {
    private final PnLibrary library;

    BusEventPages(PnLibrary library) {
        this.library = library;
    }

    private EventInspector bus() {
        return library.getEvents().getInspector();
    }

    void list(CommandContext context, String word, int requestedPage) {
        ChatKit kit = CommandOutput.kit(context);
        List<EventInfo> all = bus().events();
        BusFilter filter = BusFilter.named(word);
        List<EventInfo> shown = new ArrayList<>();
        for (EventInfo info : all) {
            if (filter.keeps(info)) {
                shown.add(info);
            }
        }
        int pages = ChatPager.pageCount(shown.size(), PAGE_SIZE);
        int page = ChatPager.clamp(requestedPage, pages);
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.header("События плагинов", kit.text(listenerCount(), ChatPalette.LABEL)));
        lines.add(tabs(kit, all, word));
        lines.add(kit.blank());
        if (shown.isEmpty()) {
            lines.add(kit.note("Здесь пока пусто."));
        }
        List<String> traced = bus().tracedEvents();
        for (EventInfo info : ChatPager.slice(shown, page, PAGE_SIZE)) {
            lines.add(row(kit, info, traced));
        }
        if (pages > 1) {
            lines.add(kit.blank());
            lines.add(ChatPager.pager(kit, ROOT + " " + word, page, pages));
        }
        lines.add(kit.blank());
        lines.addAll(listFooter(kit));
        CommandOutput.send(context, lines);
    }

    private String listenerCount() {
        int total = bus().listeners().size();
        int queued = bus().asyncQueued();
        String suffix = queued > 0 ? ", в очереди " + queued : "";
        return total + " " + RussianText.plural(total, "слушатель", "слушателя", "слушателей") + suffix;
    }

    private List<Component> listFooter(ChatKit kit) {
        List<Component> lines = new ArrayList<>(BusChat.footer(kit,
                kit.option("все слушатели", ROOT + " listeners", "Каждый слушатель каждого плагина", ChatPalette.ACCENT),
                kit.option("записи", ROOT + " traces", "Записанные вызовы", ChatPalette.ACCENT),
                kit.option("история", ROOT + " history", "Сколько раз вызывали по дням", ChatPalette.ACCENT)));
        lines.addAll(BusChat.hints(kit));
        return lines;
    }

    private Component tabs(ChatKit kit, List<EventInfo> all, String current) {
        Component line = kit.text(" Показать: ", ChatPalette.LABEL);
        boolean first = true;
        for (BusFilter filter : BusFilter.values()) {
            if (!first) {
                line = line.append(kit.text(" "));
            }
            first = false;
            line = line.append(tab(kit, all, filter, filter.getWord().equals(current)));
        }
        return line;
    }

    private Component tab(ChatKit kit, List<EventInfo> all, BusFilter filter, boolean open) {
        int count = 0;
        for (EventInfo info : all) {
            if (filter.keeps(info)) {
                count++;
            }
        }
        net.kyori.adventure.text.format.TextColor body = open ? ChatPalette.ACCENT : count == 0 ? ChatPalette.FAINT : ChatPalette.SOFT;
        Component button = kit.text("[", ChatPalette.FAINT).append(kit.text(filter.getLabel() + " " + count, body))
                .append(kit.text("]", ChatPalette.FAINT));
        if (open || !kit.getReader().isInteractive()) {
            return button;
        }
        String command = ROOT + " " + filter.getWord();
        return button.clickEvent(ClickEvent.runCommand(command)).hoverEvent(
                kit.tooltip(kit.text("Нажмите, чтобы показать: " + filter.getLabel(), ChatPalette.ACCENT), kit.blank(),
                        kit.text(command, ChatPalette.LABEL)));
    }

    private Component row(ChatKit kit, EventInfo info, List<String> traced) {
        boolean tracing = traced.contains(info.getEventType());
        Component line = kit.text(" ")
                .append(kit.text(kit.glyph('●'), info.getListeners() > 0 ? ChatPalette.OK : ChatPalette.FAINT))
                .append(kit.text(" " + CoreFormatting.shortName(info.getEventType()), ChatPalette.ACCENT))
                .append(kit.text("  " + info.getListeners() + " "
                        + RussianText.plural(info.getListeners(), "слушатель", "слушателя", "слушателей"), ChatPalette.TEXT))
                .append(kit.text("  " + info.getCalls() + " " + RussianText.plural(info.getCalls(), "вызов", "вызова", "вызовов"),
                        ChatPalette.SOFT));
        if (info.getDenied() > 0) {
            String word = RussianText.plural(info.getDenied(), "отказ", "отказа", "отказов");
            line = line.append(kit.text("  " + info.getDenied() + " " + word, ChatPalette.DANGER));
        }
        if (!kit.getReader().isInteractive()) {
            return tracing ? line.append(kit.text("  запись", ChatPalette.WARN)) : line;
        }
        line = line.append(kit.text("  ")).append(traceButton(kit, info, tracing));
        return line.hoverEvent(rowTooltip(kit, info)).clickEvent(ClickEvent.runCommand(ROOT + " view " + info.getEventType()));
    }

    private Component traceButton(ChatKit kit, EventInfo info, boolean tracing) {
        if (tracing) {
            return kit.option(kit.glyph('●') + " запись", ROOT + " trace off " + info.getEventType(),
                    "Остановить запись вызовов", ChatPalette.WARN);
        }
        return kit.option("запись", ROOT + " trace " + info.getEventType(), "Записывать каждый вызов этого события",
                ChatPalette.SOFT);
    }

    private net.kyori.adventure.text.event.HoverEvent<Component> rowTooltip(ChatKit kit, EventInfo info) {
        return kit.tooltip(
                kit.text(CoreFormatting.shortName(info.getEventType()), ChatPalette.ACCENT),
                kit.text(info.getEventType(), ChatPalette.LABEL),
                kit.blank(),
                kit.hoverField("Слушателей", kit.text(String.valueOf(info.getListeners()), ChatPalette.TEXT)),
                kit.hoverField("Вызовов", kit.text(String.valueOf(info.getCalls()), ChatPalette.TEXT)),
                kit.hoverField("Отказов", kit.text(String.valueOf(info.getDenied()),
                        info.getDenied() > 0 ? ChatPalette.DANGER : ChatPalette.TEXT)),
                kit.blank(),
                kit.text("Нажмите, чтобы открыть событие ›", ChatPalette.ACCENT));
    }

    /** Names offered while typing: short ones, and the full name only where two events share a short one. */
    List<String> eventNames() {
        List<String> all = new ArrayList<>();
        for (EventInfo info : bus().events()) {
            all.add(info.getEventType());
        }
        List<String> names = new ArrayList<>();
        for (String name : all) {
            int same = 0;
            for (String other : all) {
                if (CoreFormatting.shortName(other).equals(CoreFormatting.shortName(name))) {
                    same++;
                }
            }
            names.add(same > 1 ? name : CoreFormatting.shortName(name));
        }
        return names;
    }

    void view(CommandContext context, String typed) {
        String name = BusChat.resolve(bus(), typed);
        ChatKit kit = CommandOutput.kit(context);
        EventInfo info = null;
        for (EventInfo candidate : bus().events()) {
            if (candidate.getEventType().equals(name)) {
                info = candidate;
            }
        }
        if (info == null) {
            context.getSender().sendMessage(kit.text("Такого события нет: " + name + ". Список: " + ROOT, ChatPalette.DANGER));
            return;
        }
        Class<? extends Event> type = bus().eventType(name);
        List<ListenerInfo> listeners = type == null ? new ArrayList<>() : bus().listeners(type);
        CommandOutput.send(context, viewLines(kit, name, info, listeners));
    }

    private List<Component> viewLines(ChatKit kit, String name, EventInfo info, List<ListenerInfo> listeners) {
        boolean tracing = bus().tracedEvents().contains(name);
        Component status = info.getListeners() > 0 ? kit.text("слушают", ChatPalette.OK) : kit.text("никто не слушает", ChatPalette.FAINT);
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.header(CoreFormatting.shortName(name), status));
        lines.add(kit.text("  " + name, ChatPalette.LABEL));
        lines.add(kit.blank());
        lines.add(kit.field("Слушателей", kit.text(String.valueOf(info.getListeners()), ChatPalette.TEXT)));
        lines.add(kit.field("Вызовов", kit.text(String.valueOf(info.getCalls()), ChatPalette.TEXT)));
        lines.add(kit.field("Отказов", kit.text(String.valueOf(info.getDenied()),
                info.getDenied() > 0 ? ChatPalette.DANGER : ChatPalette.TEXT)));
        lines.add(kit.setting("Запись вызовов", traceSwitch(kit, name, tracing)));
        lines.add(kit.blank());
        lines.add(kit.section("Слушатели"));
        if (listeners.isEmpty()) {
            lines.add(kit.note("   Никто не слушает это событие."));
        }
        for (ListenerInfo listener : listeners) {
            lines.add(BusChat.eventListenerRow(kit, listener));
        }
        lines.add(kit.blank());
        lines.addAll(BusChat.footer(kit, BusChat.backToEvents(kit),
                kit.button("записи", ROOT + " traces " + name, "Записанные вызовы этого события", ChatPalette.ACCENT),
                kit.button("история", ROOT + " history of " + name, "Вызовы этого события по дням", ChatPalette.ACCENT)));
        if (!kit.getReader().isInteractive()) {
            lines.add(kit.note("Запись: " + ROOT + " trace " + name + " | " + ROOT + " trace off " + name));
        }
        return lines;
    }

    private Component traceSwitch(ChatKit kit, String name, boolean on) {
        if (!kit.getReader().isInteractive()) {
            return kit.text(on ? "включена" : "выключена", on ? ChatPalette.OK : ChatPalette.SOFT);
        }
        Component enable = on ? kit.selected("вкл", ChatPalette.OK)
                : kit.option("вкл", ROOT + " trace " + name, "Записывать каждый вызов этого события", ChatPalette.SOFT);
        Component disable = !on ? kit.selected("выкл", ChatPalette.SOFT)
                : kit.option("выкл", ROOT + " trace off " + name, "Перестать записывать; записанное останется", ChatPalette.SOFT);
        return enable.append(kit.text(" ")).append(disable);
    }

    void startTrace(CommandContext context, String typed) {
        Class<? extends Event> type = knownType(context, typed);
        if (type != null) {
            bus().enableTracing(type);
            view(context, type.getName());
        }
    }

    void stopTrace(CommandContext context, String typed) {
        Class<? extends Event> type = knownType(context, typed);
        if (type != null) {
            bus().disableTracing(type);
            view(context, type.getName());
        }
    }

    private Class<? extends Event> knownType(CommandContext context, String typed) {
        String name = BusChat.resolve(bus(), typed);
        Class<? extends Event> type = bus().eventType(name);
        if (type == null) {
            context.getSender().sendMessage(CommandOutput.kit(context)
                    .text("Такого события нет: " + name + ". Список: " + ROOT, ChatPalette.DANGER));
        }
        return type;
    }
}
