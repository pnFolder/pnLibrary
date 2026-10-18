package ru.privatenull.pnlibrary.bukkit.entity

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack

/**
 * Armor-stand visual for Bukkit animations. Call from the entity's owning server thread.
 * [location] represents the displayed item/text, with the stand offset handled internally.
 */
class VisualEntity private constructor(
    private val stand: ArmorStand,
    private val offset: Double,
    initialLocation: Location,
) {
    private var visualLocation = initialLocation.clone()

    /** Underlying entity for lifecycle tracking. */
    fun entity(): Entity = stand

    /** Whether the underlying entity is alive and valid. */
    val isValid: Boolean get() = stand.isValid && !stand.isDead

    /** Whether the visual is unavailable. */
    val isDead: Boolean get() = !isValid

    /** Defensive copy of the logical location. */
    val location: Location get() = visualLocation.clone()

    /** Moves the visual; a rejected server teleport leaves the logical location unchanged. */
    fun teleport(location: Location?) {
        val target = requireLocation(location).clone()
        if (stand.teleport(target.clone().subtract(0.0, offset, 0.0))) visualLocation = target
    }

    /** Changes yaw and pitch while keeping the logical and server locations consistent. */
    fun setRotation(yaw: Float, pitch: Float) {
        teleport(location.apply { this.yaw = yaw; this.pitch = pitch })
    }

    /** Uses the small armor-stand model for positive scales below 0.72. */
    fun setScale(scale: Float) { stand.isSmall = scale > 0f && scale < 0.72f }

    /** Displays a copy of [item] with amount one; absent or air items become a chest. */
    fun setItem(item: ItemStack?) { stand.equipment?.helmet = safeItem(item, Material.CHEST) }

    /** Replaces the displayed block, using a chest for absent or air materials. */
    fun setBlock(material: Material?) { setItem(ItemStack(blockMaterial(material))) }

    /** Replaces the floating label text. */
    fun setText(text: String?) {
        stand.customName = text.orEmpty()
        stand.isCustomNameVisible = true
    }

    /** Adds a nonblank scoreboard tag when supported by the server. */
    fun addScoreboardTag(tag: String?) {
        if (!tag.isNullOrBlank()) optionalCapability(stand, "addScoreboardTag", String::class.java, tag)
    }

    /** Removes the underlying entity; Bukkit removal is safe to repeat. */
    fun remove() { stand.remove() }

    /** Factories for item, block and text visuals. */
    companion object {
        private const val HEAD_OFFSET = 1.55
        private const val TEXT_OFFSET = 0.20

        /** Spawns an item visual at its logical display location. */
        @JvmStatic
        fun item(location: Location?, item: ItemStack?): VisualEntity {
            val target = requireLocation(location).clone()
            val stand = spawn(target.clone().subtract(0.0, HEAD_OFFSET, 0.0))
            stand.equipment?.helmet = safeItem(item, Material.NETHER_STAR)
            return VisualEntity(stand, HEAD_OFFSET, target)
        }

        /** Spawns a block visual backed by an armor-stand helmet. */
        @JvmStatic
        fun block(location: Location?, material: Material?): VisualEntity =
            item(location, ItemStack(blockMaterial(material)))

        /** Spawns a floating legacy-text label. */
        @JvmStatic
        fun text(location: Location?, text: String?): VisualEntity {
            val target = requireLocation(location).clone()
            val stand = spawn(target.clone().subtract(0.0, TEXT_OFFSET, 0.0))
            return VisualEntity(stand, TEXT_OFFSET, target).apply { setText(text) }
        }

        private fun spawn(location: Location): ArmorStand {
            val stand = location.world.spawnEntity(location, EntityType.ARMOR_STAND) as ArmorStand
            stand.isVisible = false
            stand.setGravity(false)
            stand.isMarker = true
            stand.customName = ""
            stand.isCustomNameVisible = false
            stand.setBasePlate(false)
            stand.setArms(false)
            for ((name, value) in listOf(
                "setCollidable" to false, "setSilent" to true,
                "setInvulnerable" to true, "setPersistent" to false,
            )) optionalCapability(stand, name, java.lang.Boolean.TYPE, value)
            return stand
        }

        private fun requireLocation(location: Location?): Location {
            require(location != null && location.world != null) { "Visual location must have a world" }
            return location
        }

        private fun safeItem(item: ItemStack?, fallback: Material): ItemStack =
            if (item == null || isAir(item.type)) ItemStack(fallback)
            else item.clone().apply { amount = 1 }

        private fun blockMaterial(material: Material?): Material =
            material?.takeUnless(::isAir) ?: Material.CHEST

        private fun isAir(material: Material): Boolean = material.name == "AIR" || material.name.endsWith("_AIR")

        private fun optionalCapability(receiver: Any, name: String, type: Class<*>, argument: Any) {
            try {
                receiver.javaClass.getMethod(name, type).invoke(receiver, argument)
            } catch (_: ReflectiveOperationException) {
                // This capability is absent on older servers.
            } catch (_: RuntimeException) {
                // A server implementation may reject an optional property.
            }
        }
    }
}
