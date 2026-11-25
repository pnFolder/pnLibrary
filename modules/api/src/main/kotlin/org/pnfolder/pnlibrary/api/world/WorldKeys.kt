@file:JvmName("WorldKeys")

package org.pnfolder.pnlibrary.api.world

import net.kyori.adventure.key.Key

/**
 * Turns text written in a config or a scenario into a [Key]: `speed` and `SPEED` both give `minecraft:speed`, and
 * `shop:coin` keeps its namespace. Throws when the text cannot be a key.
 */
fun minecraftKey(text: String): Key = Key.key(text.trim().lowercase())
