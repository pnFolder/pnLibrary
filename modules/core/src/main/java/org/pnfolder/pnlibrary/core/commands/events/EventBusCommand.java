package org.pnfolder.pnlibrary.core.commands.events;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.api.events.inspect.EventInspector;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/**
 * {@code /pn bus}: a window into the event bus, shared by every platform and drawn like the other {@code /pn} pages.
 *
 * <pre>
 * /pn bus [all|listened|called|denied] [page]   events with tabs, listener and call counters; click a row to open it
 * /pn bus view &lt;event&gt;                         one event: counters, a recording switch and its listeners in dispatch order
 * /pn bus listeners [page]                      every listener of every plugin
 * /pn bus slow [page]                           listeners that ran, the slowest on average first
 * /pn bus top [calls|denied]                    the ten most called (or most denied) events
 * /pn bus range [1h|24h|7d|30d]                 calls in the last hour, day, week or month, busiest first
 * /pn bus export [csv|json]                     writes the counters of days and hours to a file next to the history
 * /pn bus errors [page]                         listeners that threw, the most failures first
 * /pn bus plugin &lt;id&gt; [page]                   the listeners of one plugin with time and failures
 * /pn bus trace &lt;event&gt;, trace off &lt;event&gt;    switches the recording of an event on or off
 * /pn bus traces [event]                        recorded calls, listener by listener, with the result before and after each
 * /pn bus history [page]                        one line per day: calls, different events, denials; click a day to open it
 * /pn bus history day &lt;date&gt; [page]            the events of one day, the most called first
 * /pn bus history of &lt;event&gt; [page]            one line per day for one event
 * /pn bus clear                                 forgets the recorded calls
 * </pre>
 *
 * Requires {@code pnlibrary.events.bus}; the console always may use it. A reader that cannot click gets the same rows
 * and a line with the commands to type.
 */
public final class EventBusCommand {
    public static final String PERMISSION = "pnlibrary.events.bus";
    private static final int MAX_SUGGESTED_DAYS = 31;

    private final PnLibrary library;
    private final BusEventPages events;
    private final BusListenerPages listeners;
    private final BusTracePages traces;
    private final BusHistoryPages history;

    public EventBusCommand(@NotNull PnLibrary library) {
        this.library = library;
        this.events = new BusEventPages(library);
        this.listeners = new BusListenerPages(library);
        this.traces = new BusTracePages(library);
        this.history = new BusHistoryPages(library);
    }

    private EventInspector bus() {
        return library.getEvents().getInspector();
    }

    @NotNull
    public CommandDefinition definition() {
        return CommandDefinition.command("bus", root -> {
            root.permission(PERMISSION);
            root.noPermissionMessage("События плагинов доступны администраторам. Нужно право " + PERMISSION + ".");
            root.consoleBypassesPermission();
            eventViews(root);
            listenerViews(root);
            problemViews(root);
            traceViews(root);
            historyViews(root);
        });
    }

    private static int number(CommandContext context) {
        return context.<Integer>get("page");
    }

    private static void withPage(CommandNodeBuilder node, final Consumer<CommandContext> first, final BiConsumer<CommandContext, Integer> at) {
        node.executes(first);
        node.argument("page", ArgumentType.integer(), page -> page.executes(context -> at.accept(context, number(context))));
    }

    private void eventViews(CommandBuilder root) {
        root.executes(context -> events.list(context, "all", 1));
        for (final BusFilter filter : BusFilter.values()) {
            root.literal(filter.getWord(), node -> withPage(node,
                    context -> events.list(context, filter.getWord(), 1),
                    (context, page) -> events.list(context, filter.getWord(), page)));
        }
        root.literal("view", node -> node.argument("event", ArgumentType.string(), event -> {
            event.suggests(context -> events.eventNames());
            event.executes(context -> events.view(context, context.<String>get("event")));
        }));
    }

    private void listenerViews(CommandBuilder root) {
        root.literal("listeners", node -> withPage(node, context -> listeners.listeners(context, 1), listeners::listeners));
        root.literal("slow", node -> withPage(node, context -> listeners.slow(context, 1), listeners::slow));
        root.literal("top", node -> {
            node.executes(listeners::topCalls);
            node.literal("calls", calls -> calls.executes(listeners::topCalls));
            node.literal("denied", denied -> denied.executes(listeners::topDenied));
        });
        root.literal("range", node -> {
            node.executes(context -> listeners.range(context, "24h"));
            for (final String word : BusConstants.RANGES.keySet()) {
                node.literal(word, period -> period.executes(context -> listeners.range(context, word)));
            }
        });
        root.literal("export", node -> {
            node.executes(listeners::exportCsv);
            node.literal("csv", csv -> csv.executes(listeners::exportCsv));
            node.literal("json", json -> json.executes(listeners::exportJson));
        });
    }

    private void problemViews(CommandBuilder root) {
        root.literal("errors", node -> withPage(node, context -> listeners.errors(context, 1), listeners::errors));
        root.literal("plugin", node -> node.argument("id", ArgumentType.string(), id -> {
            id.suggests(context -> pluginIds());
            id.executes(context -> listeners.pluginView(context, context.<String>get("id"), 1));
            id.argument("page", ArgumentType.integer(), page -> page.executes(
                    context -> listeners.pluginView(context, context.<String>get("id"), number(context))));
        }));
    }

    private List<String> pluginIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<String>();
        for (ListenerInfo info : bus().listeners()) {
            ids.add(info.getPlugin().toString());
        }
        return new ArrayList<String>(ids);
    }

    private List<String> tracedNames() {
        List<String> names = new ArrayList<String>();
        for (String traced : bus().tracedEvents()) {
            names.add(CoreFormatting.shortName(traced));
        }
        return names;
    }

    private void traceViews(CommandBuilder root) {
        root.literal("trace", node -> {
            // Without a name the page lists the events, each with its own recording switch, so nothing has to be remembered.
            node.executes(context -> events.list(context, "all", 1));
            node.literal("off", off -> off.argument("event", ArgumentType.string(), event -> {
                event.suggests(context -> tracedNames());
                event.executes(context -> events.stopTrace(context, context.<String>get("event")));
            }));
            node.argument("event", ArgumentType.string(), event -> {
                event.suggests(context -> events.eventNames());
                event.executes(context -> events.startTrace(context, context.<String>get("event")));
            });
        });
        root.literal("traces", node -> {
            node.executes(context -> traces.traces(context, null));
            node.argument("event", ArgumentType.string(), event -> {
                event.suggests(context -> tracedNames());
                event.executes(context -> traces.traces(context, context.<String>get("event")));
            });
        });
        root.literal("clear", node -> node.executes(context -> {
            bus().clearTraces();
            traces.traces(context, null);
        }));
    }

    private void historyViews(CommandBuilder root) {
        root.literal("history", node -> {
            withPage(node, context -> history.history(context, null, 1), (context, page) -> history.history(context, null, page));
            node.literal("day", day -> day.argument("date", ArgumentType.string(), date -> {
                date.suggests(context -> historyDates());
                date.executes(context -> history.historyDay(context, context.<String>get("date"), 1));
                date.argument("page", ArgumentType.integer(), page -> page.executes(
                        context -> history.historyDay(context, context.<String>get("date"), number(context))));
            }));
            node.literal("of", of -> of.argument("event", ArgumentType.string(), event -> {
                event.suggests(context -> events.eventNames());
                event.executes(context -> history.history(context, context.<String>get("event"), 1));
                event.argument("page", ArgumentType.integer(), page -> page.executes(
                        context -> history.history(context, context.<String>get("event"), number(context))));
            }));
        });
    }

    private List<String> historyDates() {
        LinkedHashSet<String> dates = new LinkedHashSet<String>();
        for (EventDay day : bus().history()) {
            dates.add(day.getDate().toString());
        }
        List<String> all = new ArrayList<String>(dates);
        return all.subList(0, Math.min(MAX_SUGGESTED_DAYS, all.size()));
    }
}
