package org.pnfolder.pnlibrary.core.commands.events

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.events.DispatchMode
import org.pnfolder.pnlibrary.api.events.EventInspector
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.api.runtime.PnLibrary

/** Chat pages with event trace output. */
internal class BusTracePages(private val library: PnLibrary) {
    private val bus: EventInspector get() = library.events.inspector

    fun traces(context: CommandContext, typed: String?) {
        val name = typed?.let(bus::resolve)
        val kit = kit(context)
        val all = bus.traces().filter { name == null || it.eventName == name }
        val lines = mutableListOf(
            kit.blank(),
            kit.header("Записанные вызовы", kit.text("${all.size}", ChatPalette.LABEL)),
            kit.blank(),
        )
        if (all.isEmpty()) lines += kit.note("Пусто. Включите запись на странице события: $ROOT view <событие>")
        all.takeLast(MAX_TRACES).reversed().forEach { lines += traceBlock(kit, it) }
        lines += footer(kit, backToEvents(kit),
            if (all.isEmpty()) null else kit.button("очистить", "$ROOT clear", "Забыть записанные вызовы", ChatPalette.DANGER))
        lines.forEach(context.sender::send)
    }

    /** `sync` runs on the caller's thread, `async` on a background one where the world must not be touched. */
    private fun modeTag(kit: ChatKit, mode: DispatchMode): Component =
        if (mode == DispatchMode.ASYNC) kit.text("async (мир не трогать)", ChatPalette.WARN) else kit.text("sync", ChatPalette.OK)

    private fun traceBlock(kit: ChatKit, trace: EventTrace): List<Component> {
        val verdict = if (trace.allowed) kit.text("разрешено", ChatPalette.OK) else kit.text("отклонено", ChatPalette.DANGER)
        val lines = mutableListOf(
            kit.text(" ").append(kit.text(shortName(trace.eventName), ChatPalette.ACCENT)).append(kit.dot()).append(verdict)
                .append(kit.text("  результат: ${trace.result ?: "-"}", ChatPalette.SOFT)),
            kit.text("   ").append(modeTag(kit, trace.mode)).append(kit.text("  поток ${trace.thread}", ChatPalette.FAINT)),
        )
        trace.steps.forEach { step ->
            val owner = step.module?.let { "${step.plugin}/$it" } ?: step.plugin.toString()
            val what = when {
                step.skipped -> kit.text("пропущен (ignoreDenied)", ChatPalette.FAINT)
                step.failed -> kit.text("упал", ChatPalette.DANGER)
                step.changedResult -> kit.text("${step.resultBefore} ${kit.glyph('→')} ${step.resultAfter}", ChatPalette.INFO)
                else -> kit.text("результат не менял", ChatPalette.FAINT)
            }
            lines += kit.text("   $owner", ChatPalette.TEXT).append(kit.text("  приоритет ${step.priority}  ", ChatPalette.SOFT)).append(what)
                .append(kit.text("  ${step.nanos / NANOS_PER_MICRO} мкс", ChatPalette.FAINT))
        }
        lines += kit.blank()
        return lines
    }
}
