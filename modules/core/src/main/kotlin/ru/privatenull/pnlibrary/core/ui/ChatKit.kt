package ru.privatenull.pnlibrary.core.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import ru.privatenull.pnlibrary.api.commands.CommandSender
import java.net.URI

/**
 * Who reads a message and what their client can do.
 *
 * @property interactive whether the reader can click and hover; without it buttons are left out and pages list the
 *   commands to type instead
 * @property ascii whether to avoid characters outside ASCII and Cyrillic, for consoles with a legacy code page
 */
data class ChatReader(val interactive: Boolean = true, val ascii: Boolean = false) {
    companion object {
        /** A player whose client shows buttons and hover cards. */
        @JvmField val PLAYER = ChatReader()

        /** A server console: plain text in ASCII. */
        @JvmField val CONSOLE = ChatReader(interactive = false, ascii = true)

        /** The reader behind a command [sender]: buttons only while its transport keeps them, ASCII on the console. */
        @JvmStatic
        fun of(sender: CommandSender): ChatReader = ChatReader(sender.supportsInteraction, sender.isConsole)
    }
}

/**
 * Building blocks of every pnLibrary chat page, so all of them share one look.
 *
 * A page starts with an empty line, then a bold accent title with details beside it, optional sky-blue hints,
 * blank-line separated sections of rows and a footer of bracketed buttons whose brackets are gray and whose label
 * carries the color. Details live in multi-line hover cards. A reader that cannot click gets the same rows without
 * buttons; the console additionally gets ASCII only. Stored text is only ever rendered as plain text, never
 * interpreted as markup.
 */
open class ChatKit(val reader: ChatReader) {
    /** A page: an empty line first, so it never runs into the chat above it, such as the previous page. */
    fun framed(lines: List<Component>): List<Component> = listOf(Component.empty()) + lines

    /** The first line of a page: the title in bold accent, then its details after two spaces. */
    fun header(title: String, details: Component? = null): Component {
        val name = Component.text(shown(title), ChatPalette.ACCENT).decoration(TextDecoration.BOLD, true)
        return if (details == null) name else Component.empty().append(name).append(text("  ")).append(details)
    }

    /** Sections are separated by an empty line, never by rules or frames. */
    fun blank(): Component = Component.empty()

    /** An indented `Label: value` row, the label in sky blue. */
    fun field(label: String, value: Component): Component = text("  $label: ", ChatPalette.LABEL).append(value)

    /** A `Label: value` line of a hover card. */
    fun hoverField(label: String, value: Component): Component = text("$label: ", ChatPalette.LABEL).append(value)

    /** A row the reader acts on, such as a settings choice: the label in white, so it reads as a control. */
    fun setting(label: String, value: Component): Component = text("  $label: ", ChatPalette.TEXT).append(value)

    /** A hint or description under the title. */
    fun note(value: String, color: TextColor = ChatPalette.LABEL): Component = text(" $value", color)

    /** A separator between parts of a line. */
    fun dot(): Component = text(" · ", ChatPalette.SOFT)

    /** Lines of a hover card joined with line breaks. */
    fun tooltip(vararg lines: Component): HoverEvent<Component> =
        HoverEvent.showText(lines.drop(1).fold(lines.first()) { card, line -> card.append(Component.newline()).append(line) })

    /** `[label]` with gray brackets and the label in [color]. */
    fun bracketed(label: String, color: TextColor): Component =
        text("[", ChatPalette.SOFT).append(text(label, color)).append(text("]", ChatPalette.SOFT))

    /**
     * A clickable `[label]` that runs [command], or nothing when the reader cannot click; pages list the commands
     * for such readers instead.
     */
    fun button(label: String, command: String, hint: String, color: TextColor): Component =
        if (!reader.interactive) Component.empty()
        else bracketed(label.removePrefix("[").removeSuffix("]"), color).clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(tooltip(text(hint, ChatPalette.TEXT), blank(), text(command, ChatPalette.LABEL)))

    /** A clickable `[label]` that opens [link], or nothing when the reader cannot click or the link is not safe. */
    fun linkButton(label: String, link: String, hint: String, color: TextColor): Component =
        if (!reader.interactive || !safeLink(link)) Component.empty()
        else bracketed(label, color).clickEvent(ClickEvent.openUrl(link))
            .hoverEvent(tooltip(text(hint, ChatPalette.TEXT), text(link.take(MAX_LINE), ChatPalette.LABEL)))

    /** A bracketed choice that runs [command] on click, in [color]. */
    fun option(label: String, command: String, hint: String, color: TextColor): Component =
        if (!reader.interactive) Component.empty()
        else bracketed(label, color).clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(tooltip(text(hint, ChatPalette.TEXT), blank(), text(command, ChatPalette.LABEL)))

    /** The current choice of a settings row: marked with a check and in its own color, so it reads as already pressed. */
    fun selected(label: String, color: TextColor): Component =
        Component.text(shown("[${glyph('✔')} $label]"), color).hoverEvent(tooltip(text("Выбрано сейчас", color)))

    /** [component] led by a space, or nothing when it is empty, such as a hidden button. */
    fun spaced(component: Component): Component = if (component == Component.empty()) component else text(" ").append(component)

    /** Buttons separated by spaces on one indented line, or null when the reader cannot click any of them. */
    fun actions(vararg buttons: Component?): Component? {
        val visible = buttons.filterNotNull().filter { it != Component.empty() }
        if (visible.isEmpty()) return null
        return visible.drop(1).fold(text("  ").append(visible.first())) { line, button -> line.append(text(" ")).append(button) }
    }

    /** A section heading inside a page in Russian quotes, such as `«Сегодня»` or `«Команды»`, all in the accent. */
    fun section(title: String): Component =
        text("  ${glyph('«')}$title${glyph('»')}", ChatPalette.ACCENT)

    /**
     * A `«Команды»` section: one command per line, then what it does. Placeholders such as `<игрок>` stand out in
     * the accent; a reader who can click gets the command typed into the chat box by clicking its line.
     */
    fun commandHints(vararg hints: Pair<String?, String>): List<Component> =
        listOf(section("Команды")) + hints.mapNotNull { (command, purpose) -> command?.let { commandLine(it, purpose) } }

    private fun commandLine(command: String, purpose: String): Component {
        var line = text("    ")
        PLACEHOLDER.split(command).zip(PLACEHOLDER.findAll(command).map { it.value }.toList() + "").forEach { (plain, placeholder) ->
            line = line.append(text(plain, ChatPalette.TEXT))
            if (placeholder.isNotEmpty()) line = line.append(text(placeholder, ChatPalette.ACCENT))
        }
        line = line.append(text(" ${glyph('—')} ", ChatPalette.FAINT)).append(text(purpose, ChatPalette.LABEL))
        if (!reader.interactive) return line
        val typed = command.substringBefore('<').trimEnd() + " "
        return line.clickEvent(ClickEvent.suggestCommand(typed))
            .hoverEvent(tooltip(text("Нажмите, чтобы вставить команду", ChatPalette.TEXT), text(typed.trim(), ChatPalette.LABEL)))
    }

    /** A counter such as `3 плагина`: the number in [color], the agreeing word in sky blue. */
    fun chip(count: Int, word: String, color: TextColor): Component =
        text("$count", color).append(text(" $word", ChatPalette.LABEL))

    /** One short message: a mark in the tone's color, the text, and its detail on the next line. */
    fun notice(notice: ChatNotice): List<Component> {
        val textColor = if (notice.tone == ChatTone.FAILURE) ChatPalette.DANGER else ChatPalette.TEXT
        return listOfNotNull(
            mark(notice.tone).append(text(notice.text.take(MAX_MESSAGE), textColor)),
            notice.detail?.takeIf { it.isNotBlank() }?.let { text("    ${it.take(MAX_MESSAGE)}", ChatPalette.LABEL) },
        )
    }

    /** The indented mark that opens a [notice] line, in the tone's color; text appended to it reads as a notice. */
    fun mark(tone: ChatTone): Component {
        val symbol = when (tone) {
            ChatTone.PROGRESS -> '»'
            ChatTone.INFO -> '■'
            ChatTone.SUCCESS -> '√'
            ChatTone.WARNING, ChatTone.FAILURE -> '!'
        }
        return text("  ${glyph(symbol)} ", tone.color)
    }

    /**
     * A page that asks to confirm a risky action: what will happen, one sentence per line, and one button; a reader
     * that cannot click is told the command to type.
     */
    fun confirmation(title: String, lines: List<String>, label: String, command: String, hint: String): List<Component> {
        val result = mutableListOf(header(title), blank())
        lines.forEach { result += note(it, ChatPalette.TEXT) }
        result += blank()
        result += if (reader.interactive) text("  ").append(button(label, command, hint, ChatPalette.DANGER))
        else text("  Подтвердить: ", ChatPalette.LABEL).append(text(command, ChatPalette.LABEL))
        return framed(result)
    }

    /** Plain text in [color]; the console gets the ASCII form. */
    fun text(value: String, color: TextColor? = null): Component {
        val component = Component.text(shown(value))
        return if (color == null) component else component.color(color)
    }

    /** [value] as this reader can display it. */
    fun shown(value: String): String = if (reader.ascii) asciiOnly(value) else value

    /** One glyph as this reader can display it. */
    fun glyph(symbol: Char): String = shown(symbol.toString())

    /** Whether [value] is an HTTPS link without credentials, the only kind a page may open. */
    fun safeLink(value: String): Boolean = runCatching {
        val uri = URI(value)
        value.length <= MAX_LINK && uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null
    }.getOrDefault(false)

    /** ASCII stand-ins for the glyphs used on pages; any other character outside ASCII and Cyrillic becomes `?`. */
    private fun asciiOnly(value: String): String = buildString {
        value.forEach { char ->
            when {
                char.code < 128 || char in 'А'..'я' || char == 'ё' || char == 'Ё' -> append(char)
                else -> append(ASCII[char] ?: "?")
            }
        }
    }

    companion object {
        /** The longest stored text a page shows, such as an error message. */
        const val MAX_MESSAGE = 512

        /** The longest single line a page shows, such as a link in a hover card. */
        const val MAX_LINE = 120

        private const val MAX_LINK = 4096

        /** `<игрок>`, `[сумма]` and the like inside a command. */
        private val PLACEHOLDER = Regex("""<[^>]+>|\[[^]]+]""")

        private val ASCII: Map<Char, String> = mapOf(
            '─' to "-", '■' to "*", '✔' to "+", '□' to "o", '›' to ">", '✦' to "*", '·' to "|", '»' to ">>", '«' to "<<",
            '√' to "+", '→' to "->", '—' to "-", '–' to "-", '…' to "...",
        )
    }
}
