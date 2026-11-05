package org.pnfolder.pnlibrary.bukkit.menu

import org.bukkit.inventory.ItemStack
import org.pnfolder.pnlibrary.bukkit.item.PlayerHeads
import java.util.concurrent.ConcurrentHashMap

/**
 * Textured heads for the library menu, built once per texture and copied after that.
 * A head the server cannot make is `null`, and the caller shows an ordinary item instead.
 */
internal object MenuHeads {
    private val cache = ConcurrentHashMap<String, ItemStack>()

    fun head(hash: String): ItemStack? = cache[hash]?.clone() ?: PlayerHeads.textured(hash)?.also { cache[hash] = it.clone() }
}
