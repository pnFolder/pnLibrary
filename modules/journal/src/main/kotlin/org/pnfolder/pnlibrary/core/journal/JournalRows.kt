package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette

/** Splits [text] into lines of at most [width] characters at spaces. */
fun wrap(text: String, width: Int): List<String> {
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    text.split(' ').forEach { word ->
        if (current.isNotEmpty() && current.length + 1 + word.length > width) {
            lines += current.toString()
            current = StringBuilder()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(word)
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines
}

fun levelStyle(level: JournalLevel) = when (level) {
    JournalLevel.ERROR -> "ОШИБКА" to ChatPalette.DANGER
    JournalLevel.WARNING -> "ВНИМАНИЕ" to ChatPalette.WARN
    JournalLevel.INFO -> "инфо" to ChatPalette.SOFT
}

/** A `|` in the level's color; only the first line of a row has it underlined (`&n|`), the second one is plain. */
private fun levelBar(kit: ChatKit, level: JournalLevel, underlined: Boolean): Component =
    kit.text("|", levelStyle(level).second).decoration(net.kyori.adventure.text.format.TextDecoration.UNDERLINED, underlined)

/** The level's own shape and color: a disc for an error, a triangle for a warning, a diamond for information. */
private fun levelIcon(kit: ChatKit, level: JournalLevel): Component = when (level) {
    JournalLevel.ERROR -> kit.text(kit.glyph('●'), ChatPalette.DANGER)
    JournalLevel.WARNING -> kit.text(kit.glyph('▲'), ChatPalette.WARN)
    JournalLevel.INFO -> kit.text(kit.glyph('◆'), ChatPalette.INFO)
}

/**
 * One event as a two-line block with a colored bar on its left edge: the source, the repeat count and how long
 * ago on the first line, the message on the second. [group] holds the repeats, newest first.
 */
fun row(kit: ChatKit, group: List<JournalRecord>, full: Boolean, root: String): Component {
    val record = group.first()
    val (tag, color) = levelStyle(record.level)
    val text = if (!full && record.message.length > CHAT_CHARS) record.message.take(CHAT_CHARS) + "…" else record.message
    val firstBar = levelBar(kit, record.level, underlined = true)
    val secondBar = levelBar(kit, record.level, underlined = false)
    val icon = levelIcon(kit, record.level)
    val hover = mutableListOf<Component>(
        icon.append(kit.text(" $tag", color)).append(kit.dot()).append(kit.text(record.source, ChatPalette.ACCENT)),
        kit.blank(),
    )
    wrap(record.message.lineSequence().first(), HOVER_WIDTH).take(HOVER_MESSAGE_LINES).forEach { hover += kit.text(it, ChatPalette.TEXT) }
    hover += kit.blank()
    hover += kit.hoverField("Последний раз", kit.text(fullStamp(record.timeMillis), ChatPalette.TEXT))
    if (group.size > 1) {
        hover += kit.hoverField("Первый раз", kit.text(fullStamp(group.last().timeMillis), ChatPalette.TEXT))
        hover += kit.hoverField("Повторов", kit.text(group.size.toString(), ChatPalette.ORANGE))
    }
    hover += kit.blank()
    hover += kit.text("Нажмите, чтобы открыть карточку ›", ChatPalette.ACCENT)
    var first = kit.text(" ").append(firstBar).append(kit.text(" ")).append(icon).append(kit.text(" "))
        .append(kit.text(record.source, ChatPalette.ACCENT))
    if (group.size > 1) first = first.append(kit.text("  ×${group.size}", ChatPalette.ORANGE))
    first = first.append(kit.text("  ${ago(record.timeMillis)}", agoColor(record.timeMillis)))
    val second = kit.text(" ").append(secondBar).append(kit.text("   $text", ChatPalette.TEXT))
    return first.append(Component.newline()).append(second)
        .hoverEvent(kit.tooltip(*hover.toTypedArray()))
        .clickEvent(ClickEvent.runCommand("$root view ${record.fingerprint}"))
}
