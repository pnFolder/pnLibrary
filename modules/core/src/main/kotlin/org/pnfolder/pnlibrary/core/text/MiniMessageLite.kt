package org.pnfolder.pnlibrary.core.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * MiniMessage subset for servers whose own Adventure is too old for the real one. It only calls long-standing
 * Adventure methods (`Component.text`, `color`, `decoration`, `append`). Colors by name, `#rrggbb`, `color:`,
 * decorations, `reset`, `br`/`newline` and escaping with a backslash match MiniMessage; unknown tags stay plain text.
 * `gradient`, `transition` and `rainbow` degrade to their first color or to no color.
 */
internal object MiniMessageLite {
    private class Entry(val name: String, val color: TextColor?, val decoration: Pair<TextDecoration, Boolean>?)

    private val COLORS: Map<String, TextColor> = mapOf(
        "black" to NamedTextColor.BLACK, "dark_blue" to NamedTextColor.DARK_BLUE,
        "dark_green" to NamedTextColor.DARK_GREEN, "dark_aqua" to NamedTextColor.DARK_AQUA,
        "dark_red" to NamedTextColor.DARK_RED, "dark_purple" to NamedTextColor.DARK_PURPLE,
        "gold" to NamedTextColor.GOLD, "gray" to NamedTextColor.GRAY, "grey" to NamedTextColor.GRAY,
        "dark_gray" to NamedTextColor.DARK_GRAY, "dark_grey" to NamedTextColor.DARK_GRAY,
        "blue" to NamedTextColor.BLUE, "green" to NamedTextColor.GREEN, "aqua" to NamedTextColor.AQUA,
        "red" to NamedTextColor.RED, "light_purple" to NamedTextColor.LIGHT_PURPLE,
        "yellow" to NamedTextColor.YELLOW, "white" to NamedTextColor.WHITE,
    )
    private val DECORATIONS: Map<String, TextDecoration> = mapOf(
        "bold" to TextDecoration.BOLD, "b" to TextDecoration.BOLD,
        "italic" to TextDecoration.ITALIC, "i" to TextDecoration.ITALIC, "em" to TextDecoration.ITALIC,
        "underlined" to TextDecoration.UNDERLINED, "u" to TextDecoration.UNDERLINED,
        "strikethrough" to TextDecoration.STRIKETHROUGH, "st" to TextDecoration.STRIKETHROUGH,
        "obfuscated" to TextDecoration.OBFUSCATED, "obf" to TextDecoration.OBFUSCATED,
    )
    private val COLOR_PREFIXES = setOf("color", "c", "colour")
    private val FADES = setOf("gradient", "transition", "rainbow")

    fun deserialize(input: String): Component {
        val stack = ArrayList<Entry>()
        val text = StringBuilder()
        val parts = ArrayList<Component>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && input.startsWith("<", i + 1)) {
                text.append('<')
                i += 2
                continue
            }
            val end = if (c == '<') tagEnd(input, i) else -1
            if (end < 0 || !apply(input.substring(i + 1, end), stack, text, parts)) {
                text.append(c)
                i++
                continue
            }
            i = end + 1
        }
        flush(text, stack, parts)
        return parts.fold(Component.text()) { builder, part -> builder.append(part) }.build()
    }

    private fun tagEnd(input: String, start: Int): Int {
        for (j in start + 1 until input.length) {
            if (input[j] == '>') return j
            if (input[j] == '<') return -1
        }
        return -1
    }

    /** Returns false for an unknown tag or a closing tag with nothing open, so the caller keeps it as text. */
    private fun apply(tag: String, stack: MutableList<Entry>, text: StringBuilder, parts: MutableList<Component>): Boolean {
        val lower = tag.lowercase()
        val closing = lower.startsWith("/")
        val negated = lower.startsWith("!")
        val body = lower.removePrefix("/").removePrefix("!")
        val args = body.split(':')
        val name = args[0]
        if (!closing && !negated && (name == "br" || name == "newline")) {
            flush(text, stack, parts)
            parts += Component.text("\n")
            return true
        }
        if (!closing && !negated && name == "reset") {
            flush(text, stack, parts)
            stack.clear()
            return true
        }
        val entry = entryFor(name, args, negated, closing) ?: return false
        val index = if (closing) stack.indexOfLast { it.name == entry.name } else -1
        if (closing && index < 0) return false
        flush(text, stack, parts)
        if (closing) while (stack.size > index) stack.removeAt(stack.size - 1) else stack += entry
        return true
    }

    private fun entryFor(name: String, args: List<String>, negated: Boolean, closing: Boolean): Entry? {
        DECORATIONS[name]?.let { return Entry(it.name, null, it to !negated) }
        if (negated) return null
        if (name in COLOR_PREFIXES && closing) return Entry("color", null, null)
        if (name in COLOR_PREFIXES) return parseColor(args.getOrNull(1))?.let { Entry("color", it, null) }
        if (name in FADES) return Entry(name, args.drop(1).firstNotNullOfOrNull(::parseColor), null)
        return parseColor(name)?.let { Entry(name, it, null) }
    }

    private fun parseColor(value: String?): TextColor? {
        if (value == null) return null
        COLORS[value]?.let { return it }
        val hex = value.removePrefix("#")
        if (!value.startsWith("#") || hex.length != 6 || hex.any { Character.digit(it, 16) < 0 }) return null
        return TextColor.color(hex.toInt(16))
    }

    private fun flush(text: StringBuilder, stack: List<Entry>, parts: MutableList<Component>) {
        if (text.isEmpty()) return
        var part = Component.text(text.toString())
        stack.lastOrNull { it.color != null }?.color?.let { part = part.color(it) }
        for ((decoration, state) in stack.mapNotNull { it.decoration }.associate { it }) {
            part = part.decoration(decoration, state)
        }
        parts += part
        text.setLength(0)
    }
}
