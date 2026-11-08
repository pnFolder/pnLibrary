package org.pnfolder.pnlibrary.core.updates.databases

/** Picks tables or collections by name: an empty list takes all of them, `*` in a name stands for any text (`log_*`). */
internal object TableMask {
    /** True when [name] is taken by [masks]. */
    fun matches(masks: List<String>, name: String): Boolean = masks.isEmpty() || masks.any { mask ->
        Regex(mask.split('*').joinToString(".*") { Regex.escape(it) }).matches(name)
    }
}
