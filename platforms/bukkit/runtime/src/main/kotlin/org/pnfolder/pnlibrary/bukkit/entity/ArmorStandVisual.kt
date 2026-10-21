package org.pnfolder.pnlibrary.bukkit.entity

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack

/**
 * [VisualEntity] backed by an invisible marker armor stand. The stand stands [offset] below the visible location:
 * an item is worn on the head, a label floats just above the stand.
 */
internal class ArmorStandVisual private constructor(
    private val stand: ArmorStand,
    private val offset: Double,
    initialLocation: Location,
) : VisualEntity {
    private var visualLocation = initialLocation.clone()

    override val entity: Entity get() = stand

    override val isValid: Boolean get() = stand.isValid && !stand.isDead

    override val location: Location get() = visualLocation.clone()

    override fun teleport(location: Location) {
        val target = requireWorld(location).clone()
        if (stand.teleport(target.clone().subtract(0.0, offset, 0.0))) visualLocation = target
    }

    override fun setRotation(yaw: Float, pitch: Float) {
        teleport(location.apply { this.yaw = yaw; this.pitch = pitch })
    }

    override fun setScale(scale: Float) { stand.isSmall = scale > 0f && scale < 0.72f }

    override fun setItem(item: ItemStack?) { stand.equipment?.helmet = safeItem(item, Material.CHEST) }

    override fun setBlock(material: Material?) { setItem(ItemStack(blockMaterial(material))) }

    override fun setText(text: String?) {
        stand.customName = text.orEmpty()
        stand.isCustomNameVisible = true
    }

    override fun addScoreboardTag(tag: String) {
        if (tag.isNotBlank()) optionalCapability(stand, "addScoreboardTag", String::class.java, tag)
    }

    override fun remove() { stand.remove() }

    /** The [VisualService] of the Bukkit runtime. */
    companion object Factory : VisualService {
        private const val HEAD_OFFSET = 1.55
        private const val TEXT_OFFSET = 0.20

        override fun item(location: Location, item: ItemStack?): VisualEntity {
            val target = requireWorld(location).clone()
            val stand = spawn(target.clone().subtract(0.0, HEAD_OFFSET, 0.0))
            stand.equipment?.helmet = safeItem(item, Material.NETHER_STAR)
            return ArmorStandVisual(stand, HEAD_OFFSET, target)
        }

        override fun block(location: Location, material: Material?): VisualEntity =
            item(location, ItemStack(blockMaterial(material)))

        override fun text(location: Location, text: String?): VisualEntity {
            val target = requireWorld(location).clone()
            val stand = spawn(target.clone().subtract(0.0, TEXT_OFFSET, 0.0))
            return ArmorStandVisual(stand, TEXT_OFFSET, target).apply { setText(text) }
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

        private fun requireWorld(location: Location): Location {
            require(location.world != null) { "Visual location must have a world" }
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
