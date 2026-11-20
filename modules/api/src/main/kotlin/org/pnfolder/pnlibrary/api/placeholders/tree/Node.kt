package org.pnfolder.pnlibrary.api.placeholders.tree

import java.util.concurrent.CompletionStage

/** One branch of a placeholder tree before it is turned into registered placeholders. */
internal class Node(var segment: Segment, val valueType: Class<*>) {
    val children = mutableListOf<Node>()
    var value: ((PlaceholderCall<Any?>) -> CompletionStage<Any?>)? = null
    var format: FormatChoice = FormatChoice.Inherit
    var access: AccessRule? = null
    var inside: Boolean = false
    var updater: Updater? = null
}
