package org.pnfolder.pnlibrary.api.placeholders.info

/**
 * One format as an administrator sees it in lists.
 *
 * @property name the word after the bar in `{value|name}`
 * @property input what kind of value it accepts, such as `Number`
 */
data class FormatInfo(val name: String, val input: String)
