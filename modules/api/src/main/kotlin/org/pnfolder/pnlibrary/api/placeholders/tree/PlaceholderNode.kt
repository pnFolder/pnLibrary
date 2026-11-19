package org.pnfolder.pnlibrary.api.placeholders.tree


/** One part of a placeholder path: a fixed word or an argument. */
internal sealed class Segment {
    class Literal(val name: String) : Segment()
    class Arg(val name: String, val type: ArgType<*>) : Segment()
}
