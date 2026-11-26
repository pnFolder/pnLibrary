package org.pnfolder.pnlibrary.core.commands.events;

import static org.pnfolder.pnlibrary.core.commands.events.BusConstants.ROOT;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.events.bus.DispatchMode;
import org.pnfolder.pnlibrary.api.events.inspect.EventInspector;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** Chat pages with event trace output. */
final class BusTracePages {
    private static final int MAX_TRACES = 6;

    private final PnLibrary library;

    BusTracePages(PnLibrary library) {
        this.library = library;
    }

    private EventInspector bus() {
        return library.getEvents().getInspector();
    }

    void traces(CommandContext context, String typed) {
        String name = typed == null ? null : BusChat.resolve(bus(), typed);
        ChatKit kit = CommandOutput.kit(context);
        List<EventTrace> all = new ArrayList<EventTrace>();
        for (EventTrace trace : bus().traces()) {
            if (name == null || trace.getEventName().equals(name)) {
                all.add(trace);
            }
        }
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header("Записанные вызовы", kit.text(String.valueOf(all.size()), ChatPalette.LABEL)));
        lines.add(kit.blank());
        if (all.isEmpty()) {
            lines.add(kit.note("Пусто. Включите запись на странице события: " + ROOT + " view <событие>"));
        }
        for (int index = all.size() - 1; index >= Math.max(0, all.size() - MAX_TRACES); index--) {
            lines.addAll(traceBlock(kit, all.get(index)));
        }
        Component clear = all.isEmpty() ? null : kit.button("очистить", ROOT + " clear", "Забыть записанные вызовы", ChatPalette.DANGER);
        lines.addAll(BusChat.footer(kit, BusChat.backToEvents(kit), clear));
        CommandOutput.send(context, lines);
    }

    /** {@code sync} runs on the caller's thread, {@code async} on a background one where the world must not be touched. */
    private Component modeTag(ChatKit kit, DispatchMode mode) {
        return mode == DispatchMode.ASYNC ? kit.text("async (мир не трогать)", ChatPalette.WARN) : kit.text("sync", ChatPalette.OK);
    }

    private List<Component> traceBlock(ChatKit kit, EventTrace trace) {
        Component verdict = trace.getAllowed() ? kit.text("разрешено", ChatPalette.OK) : kit.text("отклонено", ChatPalette.DANGER);
        String result = trace.getResult() == null ? "-" : trace.getResult();
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.text(" ").append(kit.text(CoreFormatting.shortName(trace.getEventName()), ChatPalette.ACCENT))
                .append(kit.dot()).append(verdict).append(kit.text("  результат: " + result, ChatPalette.SOFT)));
        lines.add(kit.text("   ").append(modeTag(kit, trace.getMode()))
                .append(kit.text("  поток " + trace.getThread(), ChatPalette.FAINT)));
        for (EventTrace.Step step : trace.getSteps()) {
            lines.add(stepLine(kit, step));
        }
        lines.add(kit.blank());
        return lines;
    }

    private Component stepLine(ChatKit kit, EventTrace.Step step) {
        String owner = step.getModule() == null ? step.getPlugin().toString() : step.getPlugin() + "/" + step.getModule();
        Component what;
        if (step.getSkipped()) {
            what = kit.text("пропущен (ignoreDenied)", ChatPalette.FAINT);
        } else if (step.getFailed()) {
            what = kit.text("упал", ChatPalette.DANGER);
        } else if (step.getChangedResult()) {
            what = kit.text(step.getResultBefore() + " " + kit.glyph('→') + " " + step.getResultAfter(), ChatPalette.INFO);
        } else {
            what = kit.text("результат не менял", ChatPalette.FAINT);
        }
        return kit.text("   " + owner, ChatPalette.TEXT)
                .append(kit.text("  приоритет " + step.getPriority() + "  ", ChatPalette.SOFT)).append(what)
                .append(kit.text("  " + step.getNanos() / CoreFormatting.NANOS_PER_MICRO + " мкс", ChatPalette.FAINT));
    }
}
