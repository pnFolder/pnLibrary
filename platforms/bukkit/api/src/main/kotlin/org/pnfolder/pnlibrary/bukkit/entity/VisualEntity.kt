package org.pnfolder.pnlibrary.bukkit.entity

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack

/**
 * A floating item, block or text label for animations and holograms. Create it with [VisualService].
 *
 * [location] is where the item or text is seen, not where the backing entity stands. Call from the thread that owns
 * the entity (the main thread, or the region thread on Folia).
 */
interface VisualEntity {

    val entity: Entity

    val isValid: Boolean

    val location: Location

    /** Moves the visual; if the server rejects the teleport, [location] stays as it was. */
    fun teleport(location: Location)

    fun setRotation(yaw: Float, pitch: Float)

    /** Uses the small model for scales between 0 and 0.72, the normal one otherwise. */
    fun setScale(scale: Float)

    /** Shows a single copy of [item]; `null` or air shows a chest. */
    fun setItem(item: ItemStack?)

    /** Shows [material] as a block; `null` or air shows a chest. */
    fun setBlock(material: Material?)

    /** Replaces the floating label; legacy colour codes are shown as colours. */
    fun setText(text: String?)

    /** Adds a scoreboard tag on servers that support them; blank tags are ignored. */
    fun addScoreboardTag(tag: String)

    /** Removes the visual. Safe to repeat. */
    fun remove()
}

/** Spawns [VisualEntity] instances. Obtain it from [org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform.visuals]. */
interface VisualService {

    /** Spawns a floating item seen at [location]; `null` or air shows a nether star. */
    fun item(location: Location, item: ItemStack?): VisualEntity

    /** Spawns a floating block seen at [location]; `null` or air shows a chest. */
    fun block(location: Location, material: Material?): VisualEntity

    fun text(location: Location, text: String?): VisualEntity
}
