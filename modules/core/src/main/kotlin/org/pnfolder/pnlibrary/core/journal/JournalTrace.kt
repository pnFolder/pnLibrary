package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.common.text.RussianText

// Stack traces in chat: colored like source code, with frames of the JDK and the server folded.

/** Stack trace lines in a player's chat card; the console prints all of them. */
internal const val CARD_TRACE_LINES = 14

/** Stack trace frame: optional module prefix, class, method and the `File.java:44` location. */
internal val FRAME = Regex("""^at\s+(?:\S*/)?([\w$.]+)\.([\w$<>]+)\((.*)\)$""")

/** Exception line: optional `Caused by: `, the class and its message. */
internal val HEAD = Regex("""^(Caused by: |Suppressed: )?([\w$.]+)(?::\s?(.*))?$""")

/** Packages of the JDK, the server and common libraries: their frames are dimmed, the plugin's own stay bright. */
internal val FRAMEWORK = listOf(
    "java.", "javax.", "jdk.", "sun.", "org.bukkit.", "io.papermc.", "net.minecraft.", "com.mojang.", "org.spigotmc.",
    "net.md_5.", "com.destroystokyo.", "io.netty.", "org.apache.", "kotlin.", "kotlinx.", "com.velocitypowered.",
    "net.kyori.", "org.jetbrains.",
)

/** One trace line as colored text, and whether it is a frame of the JDK, the server or a library. */
internal fun traceLine(kit: ChatKit, line: String): Pair<Component, Boolean> {
    val text = line.replace("\t", " ").trim()
    FRAME.matchEntire(text)?.let { match ->
        val (className, method, location) = match.destructured
        val framework = FRAMEWORK.any { className.startsWith(it) }
        val tip = kit.tooltip(
            kit.text("$className.$method", if (framework) ChatPalette.FAINT else ChatPalette.TEXT),
            kit.text(location, if (framework) ChatPalette.FAINT else ChatPalette.SOFT),
        )
        val component = if (framework) {
            kit.text("    · ", ChatPalette.FAINT)
                .append(kit.text("${className.substringAfterLast('.')}.$method", ChatPalette.FAINT))
                .append(kit.text("  $location", ChatPalette.FAINT))
        } else {
            kit.text("    ▸ ", ChatPalette.ORANGE)
                .append(kit.text(className.substringAfterLast('.'), ChatPalette.TEXT))
                .append(kit.text(".", ChatPalette.FAINT))
                .append(kit.text(method, ChatPalette.ACCENT))
                .append(kit.text("  $location", ChatPalette.SOFT))
        }
        return component.hoverEvent(tip) to framework
    }
    HEAD.matchEntire(text)?.let { match ->
        val prefix = match.groupValues[1]
        val className = match.groupValues[2]
        val message = match.groupValues[3]
        var head = kit.text("  ")
        if (prefix.isNotEmpty()) head = head.append(kit.text(prefix, ChatPalette.ORANGE))
        head = head.append(kit.text(className.substringBeforeLast('.', "").let { if (it.isEmpty()) "" else "$it." }, ChatPalette.FAINT))
            .append(kit.text(className.substringAfterLast('.'), ChatPalette.DANGER))
        if (message.isNotEmpty()) head = head.append(kit.text(": ", ChatPalette.SOFT)).append(kit.text(message, ChatPalette.TEXT))
        return head to false
    }
    return kit.text("    $text", ChatPalette.FAINT) to false
}

/**
 * The whole stack trace colored like source code: exception classes in red with their package dimmed, the plugin's
 * own frames in amber and green, frames of the JDK and the server dimmed. In chat the dimmed frames of one run are
 * folded into a single line and the list is cut; the console shows every line.
 */
internal fun traceView(kit: ChatKit, trace: List<String>, full: Boolean): Pair<List<Component>, Boolean> {
    val rendered = trace.map { traceLine(kit, it) }
    if (full) return rendered.map { it.first } to false
    val out = mutableListOf<Component>()
    var folded = 0
    var cut = 0
    fun flush() {
        if (folded == 0) return
        out += kit.text("    · … $folded ${RussianText.plural(folded.toLong(), "служебный вызов", "служебных вызова", "служебных вызовов")}", ChatPalette.FAINT)
        folded = 0
    }
    rendered.forEach { (component, framework) ->
        if (framework) {
            folded++
            return@forEach
        }
        flush()
        if (out.size >= CARD_TRACE_LINES) cut++ else out += component
    }
    flush()
    if (cut > 0) out += kit.note("    … ещё ${RussianText.counted(cut.toLong(), "строка", "строки", "строк")}, их даёт кнопка [скопировать]", ChatPalette.FAINT)
    return out to (cut > 0 || rendered.any { it.second })
}
