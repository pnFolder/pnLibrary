package org.pnfolder.pnlibrary.api.placeholders.tree

import java.util.concurrent.CompletionStage

/** One part of a placeholder path: a fixed word or an argument. */
internal sealed class Segment {
    class Literal(val name: String) : Segment()
    class Arg(val name: String, val type: ArgType<*>) : Segment()
}

/** The way to change a placeholder from outside, and who may do it. */
internal class Updater(val rule: AccessRule, val run: (PlaceholderCall<Any?>, String) -> Any?)

/** One branch of a placeholder tree before it is turned into registered placeholders. */
internal class Node(var segment: Segment, val valueType: Class<*>) {
    val children = mutableListOf<Node>()
    var value: ((PlaceholderCall<Any?>) -> CompletionStage<Any?>)? = null
    var format: FormatChoice = FormatChoice.Inherit
    var access: AccessRule? = null
    var inside: Boolean = false
    var updater: Updater? = null
}

/** The settings and branches of one `placeholders { }` block. */
internal class GroupData(val papiPrefix: String?) {
    val roots = mutableListOf<Node>()
    var format: FormatChoice = FormatChoice.Inherit
    var access: AccessRule = AccessRule.AllowAll
}
