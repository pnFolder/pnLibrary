package org.pnfolder.pnlibrary.core.commands.events

import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.events.EventInspector
import org.pnfolder.pnlibrary.api.runtime.PnLibrary

/**
 * `/pn bus`: a window into the event bus, shared by every platform and drawn like the other `/pn` pages.
 *
 * | Command | What it shows |
 * |---|---|
 * | `/pn bus [all\|listened\|called\|denied] [page]` | events with tabs, listener and call counters; click a row to open it |
 * | `/pn bus view <event>` | one event: counters, a recording switch and its listeners in dispatch order |
 * | `/pn bus listeners [page]` | every listener of every plugin |
 * | `/pn bus slow [page]` | listeners that ran, the slowest on average first |
 * | `/pn bus top [calls\|denied]` | the ten most called (or most denied) events |
 * | `/pn bus range [1h\|24h\|7d\|30d]` | calls in the last hour, day, week or month, busiest first |
 * | `/pn bus export [csv\|json]` | writes the counters of days and hours to a file next to the history |
 * | `/pn bus errors [page]` | listeners that threw, the most failures first |
 * | `/pn bus plugin <id> [page]` | the listeners of one plugin with time and failures |
 * | `/pn bus trace <event>`, `trace off <event>` | switches the recording of an event on or off |
 * | `/pn bus traces [event]` | recorded calls, listener by listener, with the result before and after each |
 * | `/pn bus history [page]` | one line per day: calls, different events, denials; click a day to open it |
 * | `/pn bus history day <date> [page]` | the events of one day, the most called first |
 * | `/pn bus history of <event> [page]` | one line per day for one event |
 * | `/pn bus clear` | forgets the recorded calls |
 *
 * Requires `pnlibrary.events.bus`; the console always may use it. A reader that cannot click gets the same rows
 * and a line with the commands to type.
 */
class EventBusCommand(private val library: PnLibrary) {
    private val bus: EventInspector get() = library.events.inspector
    private val events = BusEventPages(library)
    private val listeners = BusListenerPages(library)
    private val traces = BusTracePages(library)
    private val history = BusHistoryPages(library)

    fun definition(): CommandDefinition = command("bus") {
        permission(PERMISSION)
        noPermissionMessage("События плагинов доступны администраторам. Нужно право $PERMISSION.")
        consoleBypassesPermission()
        eventViews()
        listenerViews()
        problemViews()
        traceViews()
        historyViews()
    }

    private fun CommandBuilder.eventViews() {
        executes { events.list(it, "all", 1) }
        FILTERS.forEach { (word, _) ->
            literal(word) {
                executes { events.list(it, word, 1) }
                argument("page", ArgumentType.integer()) { executes { events.list(it, word, it.get<Int>("page")) } }
            }
        }
        literal("view") {
            argument("event", ArgumentType.string()) {
                suggests { events.eventNames() }
                executes { events.view(it, it.get<String>("event")) }
            }
        }
    }

    private fun CommandBuilder.listenerViews() {
        literal("listeners") {
            executes { listeners.listeners(it, 1) }
            argument("page", ArgumentType.integer()) { executes { listeners.listeners(it, it.get<Int>("page")) } }
        }
        literal("slow") {
            executes { listeners.slow(it, 1) }
            argument("page", ArgumentType.integer()) { executes { listeners.slow(it, it.get<Int>("page")) } }
        }
        literal("top") {
            executes { listeners.topCalls(it) }
            literal("calls") { executes { listeners.topCalls(it) } }
            literal("denied") { executes { listeners.topDenied(it) } }
        }
        literal("range") {
            executes { listeners.range(it, "24h") }
            RANGES.keys.forEach { word -> literal(word) { executes { listeners.range(it, word) } } }
        }
        literal("export") {
            executes { listeners.exportCsv(it) }
            literal("csv") { executes { listeners.exportCsv(it) } }
            literal("json") { executes { listeners.exportJson(it) } }
        }
    }

    private fun CommandBuilder.problemViews() {
        literal("errors") {
            executes { listeners.errors(it, 1) }
            argument("page", ArgumentType.integer()) { executes { listeners.errors(it, it.get<Int>("page")) } }
        }
        literal("plugin") {
            argument("id", ArgumentType.string()) {
                suggests { bus.listeners().map { it.plugin.toString() }.distinct() }
                executes { listeners.pluginView(it, it.get<String>("id"), 1) }
                argument("page", ArgumentType.integer()) { executes { listeners.pluginView(it, it.get<String>("id"), it.get<Int>("page")) } }
            }
        }
    }

    private fun CommandBuilder.traceViews() {
        literal("trace") {
            // Without a name the page lists the events, each with its own recording switch, so nothing has to be remembered.
            executes { events.list(it, "all", 1) }
            literal("off") {
                argument("event", ArgumentType.string()) {
                    suggests { bus.tracedEvents().map(::shortName) }
                    executes { events.stopTrace(it, it.get<String>("event")) }
                }
            }
            argument("event", ArgumentType.string()) {
                suggests { events.eventNames() }
                executes { events.startTrace(it, it.get<String>("event")) }
            }
        }
        literal("traces") {
            executes { traces.traces(it, null) }
            argument("event", ArgumentType.string()) {
                suggests { bus.tracedEvents().map(::shortName) }
                executes { traces.traces(it, it.get<String>("event")) }
            }
        }
        literal("clear") {
            executes {
                bus.clearTraces()
                traces.traces(it, null)
            }
        }
    }

    private fun CommandBuilder.historyViews() {
        literal("history") {
            executes { history.history(it, null, 1) }
            argument("page", ArgumentType.integer()) { executes { history.history(it, null, it.get<Int>("page")) } }
            literal("day") {
                argument("date", ArgumentType.string()) {
                    suggests { bus.history().map { it.date.toString() }.distinct().take(MAX_SUGGESTED_DAYS) }
                    executes { history.historyDay(it, it.get<String>("date"), 1) }
                    argument("page", ArgumentType.integer()) { executes { history.historyDay(it, it.get<String>("date"), it.get<Int>("page")) } }
                }
            }
            literal("of") {
                argument("event", ArgumentType.string()) {
                    suggests { events.eventNames() }
                    executes { history.history(it, it.get<String>("event"), 1) }
                    argument("page", ArgumentType.integer()) { executes { history.history(it, it.get<String>("event"), it.get<Int>("page")) } }
                }
            }
        }
    }

    companion object {
        const val PERMISSION = "pnlibrary.events.bus"
    }
}
