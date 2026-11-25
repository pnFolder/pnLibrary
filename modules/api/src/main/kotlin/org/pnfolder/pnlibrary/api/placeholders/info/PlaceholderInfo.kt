package org.pnfolder.pnlibrary.api.placeholders.info

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder
import org.pnfolder.pnlibrary.api.registry.Visibility

/**
 * One placeholder as an administrator sees it in lists.
 *
 * @property address the address with its arguments, such as `clan.war.{arg1}.score`
 * @property description the text of `@Placeholder("...")`, or an empty string
 * @property owner the plugin and module that registered it, as `plugin/module`
 * @property type the type of the value
 * @property format the format applied when the address names none, or `null`
 * @property published the names under which PlaceholderAPI shows it, such as `pnclans_clan_level`
 * @property visibility who may use it
 */
data class PlaceholderInfo(
    val address: String,
    val description: String,
    val owner: String,
    val type: String,
    val format: String? = null,
    val published: List<String> = emptyList(),
    val visibility: Visibility = Visibility.ALL,
)
