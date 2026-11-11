package org.pnfolder.pnlibrary.common.text

import java.util.regex.Pattern

/**
 * Case-insensitive wildcard matching used by access policies: `*` matches any character sequence,
 * including an empty one, and every other character is matched literally.
 */
object Wildcards {
    /** Returns whether the whole of [value] matches [pattern], ignoring case. */
    @JvmStatic
    fun matches(pattern: String, value: String): Boolean {
        val expression = pattern.split('*').joinToString(".*", transform = Pattern::quote)
        return Pattern.compile("^$expression$", Pattern.CASE_INSENSITIVE).matcher(value).matches()
    }
}
