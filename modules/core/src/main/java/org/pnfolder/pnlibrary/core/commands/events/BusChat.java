package org.pnfolder.pnlibrary.core.commands.events;

import static org.pnfolder.pnlibrary.core.commands.events.BusConstants.ROOT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.page.CommandHint;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.events.inspect.EventInspector;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** Rows, bars, hints and buttons shared by the pages of {@code /pn bus}. */
final class BusChat {
    private BusChat() {
    }

    static String bar(ChatKit kit, long value, long busiest) {
        long width = (value * CoreFormatting.BAR_WIDTH + busiest - 1) / Math.max(busiest, 1);
        int shown = (int) Math.max(1, Math.min(width, CoreFormatting.BAR_WIDTH));
        String glyph = kit.getReader().isAscii() ? "#" : "█";
        StringBuilder bar = new StringBuilder();
        for (int index = 0; index < shown; index++) {
            bar.append(glyph);
        }
        return bar.toString();
    }

    static List<Component> hints(ChatKit kit) {
        if (kit.getReader().isInteractive()) {
            return Collections.emptyList();
        }
        return kit.commandHints(
                new CommandHint(ROOT + " [all|listened|called|denied] [страница]", "список событий"),
                new CommandHint(ROOT + " view <событие>", "карточка события"),
                new CommandHint(ROOT + " listeners", "все слушатели"),
                new CommandHint(ROOT + " slow [страница]", "самые медленные слушатели"),
                new CommandHint(ROOT + " export [csv|json]", "выгрузить счётчики в файл"),
                new CommandHint(ROOT + " range [1h|24h|7d|30d]", "вызовы за период"),
                new CommandHint(ROOT + " top [calls|denied]", "самые частые события"),
                new CommandHint(ROOT + " errors [страница]", "слушатели с ошибками"),
                new CommandHint(ROOT + " plugin <плагин>", "слушатели одного плагина"),
                new CommandHint(ROOT + " trace <событие>", "записывать вызовы"),
                new CommandHint(ROOT + " traces", "записанные вызовы"),
                new CommandHint(ROOT + " history [страница]", "вызовы по дням"),
                new CommandHint(ROOT + " history day <дата> [страница]", "события одного дня"),
                new CommandHint(ROOT + " history of <событие>", "вызовы одного события по дням"));
    }

    static List<Component> footer(ChatKit kit, Component... buttons) {
        Component actions = kit.actions(buttons);
        return actions == null ? Collections.emptyList() : Collections.singletonList(actions);
    }

    static Component backToEvents(ChatKit kit) {
        return kit.button("назад", ROOT, "К списку событий", ChatPalette.ACCENT);
    }

    static String ownerLabel(ListenerInfo info) {
        return info.getModule() == null ? info.getPlugin().toString() : info.getPlugin() + "/" + info.getModule();
    }

    static Component listenerRow(ChatKit kit, ListenerInfo info) {
        Component title = Component.empty().append(kit.text(ownerLabel(info), ChatPalette.ACCENT)).append(kit.dot())
                .append(kit.text(CoreFormatting.shortName(info.getEventType()), ChatPalette.TEXT));
        return listenerRow(kit, info, title);
    }

    /** A listener on the page of its own event, where the event name would only repeat the title. */
    static Component eventListenerRow(ChatKit kit, ListenerInfo info) {
        return listenerRow(kit, info, kit.text(ownerLabel(info), ChatPalette.ACCENT));
    }

    private static Component listenerRow(ChatKit kit, ListenerInfo info, Component title) {
        Component line = kit.text("   ").append(title).append(kit.text("  приоритет " + info.getPriority(), ChatPalette.SOFT));
        if (info.getIgnoreDenied()) {
            line = line.append(kit.text("  ignoreDenied", ChatPalette.INFO));
        }
        if (info.getFailures() > 0) {
            line = line.append(kit.text("  ошибок " + info.getFailures(), ChatPalette.DANGER));
        }
        if (!kit.getReader().isInteractive()) {
            return line.append(kit.text("  " + info.getSource(), ChatPalette.FAINT));
        }
        return line.hoverEvent(listenerTooltip(kit, info)).clickEvent(ClickEvent.runCommand(ROOT + " view " + info.getEventType()));
    }

    private static net.kyori.adventure.text.event.HoverEvent<Component> listenerTooltip(ChatKit kit, ListenerInfo info) {
        return kit.tooltip(
                kit.text(ownerLabel(info), ChatPalette.ACCENT),
                kit.blank(),
                kit.hoverField("Событие", kit.text(info.getEventType(), ChatPalette.TEXT)),
                kit.hoverField("Источник", kit.text(info.getSource(), ChatPalette.TEXT)),
                kit.hoverField("Приоритет", kit.text(String.valueOf(info.getPriority()), ChatPalette.TEXT)),
                kit.hoverField("Запусков", kit.text(String.valueOf(info.getInvocations()), ChatPalette.TEXT)),
                kit.hoverField("Среднее время", kit.text(CoreFormatting.nanosText(info.getAverageNanos()), ChatPalette.TEXT)),
                kit.hoverField("Ошибок", kit.text(String.valueOf(info.getFailures()),
                        info.getFailures() > 0 ? ChatPalette.DANGER : ChatPalette.TEXT)));
    }

    /** The full name of the event the player typed: an exact name, or a short name that only one event has. */
    @NotNull
    static String resolve(EventInspector bus, String typed) {
        List<String> known = new ArrayList<>();
        for (EventInfo info : bus.events()) {
            known.add(info.getEventType());
        }
        if (known.contains(typed)) {
            return typed;
        }
        String single = null;
        int matches = 0;
        for (String name : known) {
            if (CoreFormatting.shortName(name).equalsIgnoreCase(typed)) {
                single = name;
                matches++;
            }
        }
        return matches == 1 ? single : typed;
    }
}
