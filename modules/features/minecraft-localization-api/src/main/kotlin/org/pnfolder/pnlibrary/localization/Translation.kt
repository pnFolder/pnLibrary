package org.pnfolder.pnlibrary.localization

/**
 * Result of translating a typed platform value.
 *
 * [localized] is `true` when [text] came from a translation table entry, and `false` when no candidate
 * key matched and [text] is a readable fallback derived from the value itself. [key] is the translation
 * key that produced [text], or the first candidate key when the result is a fallback.
 */
data class Translation(val key: String?, val text: String, val localized: Boolean)
