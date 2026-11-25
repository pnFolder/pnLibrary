package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import org.pnfolder.pnlibrary.api.registry.Visibility
import java.util.concurrent.CompletionStage

/** One part of the path of a placeholder: a fixed word, or a slot the address fills in. */
internal sealed class PhPart {
    class Word(val name: String) : PhPart()

    class Slot(val name: String, val type: SlotType) : PhPart()
}

/** How a slot reads its text. It never rejects a text: the method decides what to do with a wrong one. */
internal class SlotType(val label: String, val choices: List<String>, val convert: (ArgumentInput) -> CompletionStage<ParsedArgument>)

/** One branch of the tree. A node with a [value] is a placeholder; a node without one only groups. */
internal class PhNode(val part: PhPart, val valueType: Class<*>) {
    val children = mutableListOf<PhNode>()
    var value: ((Call) -> CompletionStage<Any?>)? = null
    var format: String? = null
    var description: String = ""

    /** Who may use this placeholder; set on the node that holds the value. */
    var visibility: Visibility = Visibility.ALL
}

/** What a placeholder method sees: who asks and the text of every slot on the path. */
internal class Call(val viewer: ViewerInfo, private val slots: Map<String, ParsedArgument>, val route: RouteInfo) {
    fun slot(name: String): ParsedArgument = slots[name] ?: error("The path has no argument '$name'")
}

private val WORD = Regex("[a-z0-9_-]+")

/** A word of a path: lower case letters, digits, `_` and `-`. */
internal fun checkWord(name: String): String {
    require(WORD.matches(name)) { "Placeholder part '$name' must use lowercase letters, digits, '_' or '-'" }
    return name
}

/** A value that brings the format to use when the address names none: from `Reply.value(x, format = "...")`. */
internal class Formatted(val value: Any, val format: String)

/**
 * How near something is to the module [consumer], or `null` when it may not use it: 0 its own module, 1 its own plugin,
 * 2 everyone else. [owner] and [consumer] are `plugin/module`; a `null` consumer (PlaceholderAPI, the library) sees only [Visibility.ALL].
 */
/** A consumer that sees every placeholder, hidden ones too: for the `test` command of an operator. */
internal const val EVERYTHING = "*"

internal fun nearness(owner: String, visibility: org.pnfolder.pnlibrary.api.registry.Visibility, consumer: String?): Int? = when {
    consumer == EVERYTHING -> 2
    consumer != null && owner == consumer -> 0
    visibility == org.pnfolder.pnlibrary.api.registry.Visibility.MODULE -> null
    consumer != null && owner.substringBefore('/') == consumer.substringBefore('/') -> 1
    visibility == org.pnfolder.pnlibrary.api.registry.Visibility.PLUGIN -> null
    else -> 2
}

/**
 * Does the owner written in an address fit [owner]? Nothing written fits all. `~` is the own plugin of [consumer],
 * `~~` its own module, `~~~` the library, `plugin/module` that module, `plugin` any module of that plugin.
 */
internal fun ownerFits(written: String?, owner: String, consumer: String?): Boolean = when {
    written == null -> true
    written == "~~~" -> owner.isEmpty() || owner.startsWith("pnlibrary/")
    written == "~~" -> consumer != null && owner == consumer
    written == "~" -> consumer != null && owner.substringBefore('/') == consumer.substringBefore('/')
    '/' in written -> owner.equals(written, ignoreCase = true)
    else -> owner.substringBefore('/').equals(written, ignoreCase = true)
}
