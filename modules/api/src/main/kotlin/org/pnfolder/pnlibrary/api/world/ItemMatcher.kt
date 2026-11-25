package org.pnfolder.pnlibrary.api.world

import net.kyori.adventure.key.Key

/**
 * Describes which items a player inventory check counts.
 *
 * Every text field that is `null` is not compared. Color codes written with `&` are accepted in
 * [name] and in the other text fields.
 *
 * @property material item key such as `minecraft:diamond`
 * @property name full display name the item must have
 * @property nameContains text the display name must contain
 * @property loreContains text that at least one lore line must contain
 * @property ignoreCase whether text is compared without case sensitivity
 */
data class ItemMatcher(
    val material: Key,
    val name: String? = null,
    val nameContains: String? = null,
    val loreContains: String? = null,
    val ignoreCase: Boolean = true,
)
