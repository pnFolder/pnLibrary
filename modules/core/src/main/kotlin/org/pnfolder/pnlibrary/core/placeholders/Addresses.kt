package org.pnfolder.pnlibrary.core.placeholders

/** One `|name:argument,argument` of an address. */
internal class FormatCall(val name: String, val arguments: List<String>, val owner: String? = null)

/** An address taken apart: the words of the path and the formats after it. */
internal class ParsedAddress(val words: List<String>, val formats: List<FormatCall>, val owner: String? = null)

/**
 * Reads `{group.branch.argument.field|format:arg|format}`. The braces are optional.
 *
 * An owner may stand in front, ended by `@`: `{shop/main@shop.price}` (that module), `{shop@shop.price}` (any module of
 * the plugin), `{~@shop.price}` (the own plugin only), `{~~@shop.price}` (the own module only), `{~~~@shop.price}` (the library
 * only). A format takes an owner the same way: `|shop/main@pad:4`, `|~~~@upper`.
 *
 * A part may be a placeholder itself: `{clan.of.{player.name}.level}` reads the inner one first.
 *
 * A part that holds a dot, a bar, a colon or a comma is put in quotes (`"a.b"`). `~XX` stands for the character with
 * the hexadecimal code XX, the way PlaceholderAPI texts have to write `%` (`~25`) and `"` (`~22`).
 */
internal object Addresses {
    /** Placeholders inside placeholders go this deep; more is refused. */
    const val MAX_NESTING = 16

    private val ESCAPE = Regex("~([0-9A-Fa-f]{2})")

    fun parse(text: String): ParsedAddress? {
        var body = text.trim()
        if (body.startsWith("{") && body.endsWith("}")) body = body.substring(1, body.length - 1)
        val parts = split(body, '|')
        var path = parts.first()
        var owner: String? = null
        val at = path.indexOf('@')
        if (at >= 0 && path.substring(0, at).none { it == '.' || it == '"' }) {
            owner = path.substring(0, at).trim().lowercase().also { if (it.isEmpty()) return null }
            path = path.substring(at + 1)
        }
        val words = split(path, '.').map(::decode)
        if (words.isEmpty() || words.any { it.isEmpty() }) return null
        val formats = parts.drop(1).map { part ->
            val colon = indexOfOutsideQuotes(part, ':')
            var name = (if (colon < 0) part else part.substring(0, colon)).trim()
            var formatOwner: String? = null
            name.indexOf('@').takeIf { it > 0 }?.let { formatOwner = name.substring(0, it).trim().lowercase(); name = name.substring(it + 1).trim() }
            val arguments = if (colon < 0) emptyList() else split(part.substring(colon + 1), ',').map(::decode)
            FormatCall(name.lowercase(), arguments, formatOwner)
        }
        if (formats.any { it.name.isEmpty() }) return null
        return ParsedAddress(words, formats, owner)
    }

    /**
     * The parts of an address that holds `{inner}` placeholders, as (is inner, text) pairs with the braces of the inner
     * ones removed; `null` when it holds none. Quoted text is never taken apart.
     */
    fun innerParts(body: String): List<Pair<Boolean, String>>? {
        val parts = mutableListOf<Pair<Boolean, String>>()
        val literal = StringBuilder()
        var quoted = false
        var index = 0
        while (index < body.length) {
            val character = body[index]
            if (character == '"') quoted = !quoted
            if (character == '{' && !quoted) {
                var depth = 0
                var end = index
                while (end < body.length) {
                    if (body[end] == '{') depth++ else if (body[end] == '}' && --depth == 0) break
                    end++
                }
                if (end >= body.length) return null
                if (literal.isNotEmpty()) parts += false to literal.toString()
                literal.setLength(0)
                parts += true to body.substring(index + 1, end)
                index = end + 1
                continue
            }
            literal.append(character)
            index++
        }
        if (parts.isEmpty()) return null
        if (literal.isNotEmpty()) parts += false to literal.toString()
        return parts
    }

    /** How deep `{}` pairs lie inside each other in [body] (0 when none); quoted text does not count. */
    fun depth(body: String): Int {
        var quoted = false
        var now = 0
        var deepest = 0
        for (character in body) {
            when {
                character == '"' -> quoted = !quoted
                quoted -> Unit
                character == '{' -> deepest = maxOf(deepest, ++now)
                character == '}' -> now = maxOf(0, now - 1)
            }
        }
        return deepest
    }

    /** Writes [text] so that [parse] reads it back as one part. */
    fun quote(text: String): String =
        if (text.any { it in ".|:,\"~{}" || it.isWhitespace() }) "\"" + text.replace("\"", "~22") + "\"" else text

    private fun split(text: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        var quoted = false
        val current = StringBuilder()
        for (character in text) {
            when {
                character == '"' -> { quoted = !quoted; current.append(character) }
                character == separator && !quoted -> { parts += current.toString(); current.setLength(0) }
                else -> current.append(character)
            }
        }
        parts += current.toString()
        return parts
    }

    private fun indexOfOutsideQuotes(text: String, wanted: Char): Int {
        var quoted = false
        for ((index, character) in text.withIndex()) {
            if (character == '"') quoted = !quoted else if (character == wanted && !quoted) return index
        }
        return -1
    }

    private fun decode(part: String): String {
        val trimmed = part.trim()
        val inner = if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) trimmed.substring(1, trimmed.length - 1) else trimmed
        return ESCAPE.replace(inner) { it.groupValues[1].toInt(16).toChar().toString() }
    }
}
