package org.pnfolder.pnlibrary.api.placeholders.tree


/** Turns one path part of a placeholder into a typed value; `null` means the part does not fit. */
abstract class ArgType<T : Any>(
    /** Short word shown to the administrator, such as `int` or `player`. */
    val label: String,
) {
    /** Parses [text], or returns `null` when it is not a valid value of this type. */
    abstract fun parse(text: String): T?

    /** Allowed values when the type has a fixed list, otherwise empty. */
    open val choices: List<String> get() = emptyList()
}
