package org.pnfolder.pnlibrary.bukkit.item

import org.bukkit.inventory.ItemStack
import java.util.Collections

/** Anything [ItemSpec] has no field for: flags, custom model data, persistent data. Runs last, on the created item. */
fun interface ItemCustomizer {
    /** Changes [item] in place. */
    fun apply(item: ItemStack)
}

/**
 * Description of an item, made by hand with [builder] or read from configuration with [ItemService.read].
 * [ItemService.create] turns it into an [ItemStack]; the same spec can create any number of items.
 *
 * ```kotlin
 * val sword = items.create(itemSpec {
 *     material("DIAMOND_SWORD")
 *     name("&bМеч героя")
 *     lore("&7Урон: 12", "&7Прочность: бесконечная")
 *     enchant("sharpness", 5)
 *     customize { it.itemMeta = it.itemMeta.apply { isUnbreakable = true } }
 * })
 * ```
 *
 * Text accepts `&` colour codes and `&#RRGGBB`.
 *
 * @property material Bukkit material name; an unknown one becomes stone
 * @property amount stack size, at least one
 * @property name display name, or `null` for the default
 * @property lore lore lines
 * @property enchantments enchantment names (`sharpness` or `minecraft:sharpness`) and levels
 * @property texture player-head texture: hash, `textures.minecraft.net` URL or Base64; makes the item a head
 * @property exact lossless payload from [ItemService.encode]; when valid it replaces material and texture
 * @property customizers extra changes applied in order after everything above
 */
class ItemSpec private constructor(
    val material: String,
    val amount: Int,
    val name: String?,
    val lore: List<String>,
    val enchantments: Map<String, Int>,
    val texture: String?,
    val exact: String?,
    val customizers: List<ItemCustomizer>,
) {
    /** A builder holding this spec's values, to change a few of them. */
    fun toBuilder(): Builder = Builder().also { builder ->
        builder.material(material).amount(amount).name(name).lore(lore).texture(texture).exact(exact)
        enchantments.forEach { (key, level) -> builder.enchant(key, level) }
        customizers.forEach { builder.customize(it) }
    }

    /** Fluent builder; every method returns the same builder. */
    class Builder internal constructor() {
        private var material = "STONE"
        private var amount = 1
        private var name: String? = null
        private val lore = mutableListOf<String>()
        private val enchantments = linkedMapOf<String, Int>()
        private var texture: String? = null
        private var exact: String? = null
        private val customizers = mutableListOf<ItemCustomizer>()

        /** Sets the Bukkit material name, for example `DIAMOND_SWORD`. */
        fun material(value: String): Builder = apply {
            require(value.isNotBlank()) { "Item material must not be blank" }
            material = value.trim()
        }

        /** Sets the stack size, at least one. */
        fun amount(value: Int): Builder = apply {
            require(value >= 1) { "Item amount must be at least 1" }
            amount = value
        }

        /** Sets the display name; `null` keeps the default. */
        fun name(value: String?): Builder = apply { name = value }

        /** Replaces the lore with [lines]. */
        fun lore(lines: List<String>): Builder = apply {
            lore.clear()
            lore += lines
        }

        /** Replaces the lore with [lines]. */
        fun lore(vararg lines: String): Builder = lore(lines.toList())

        /** Adds one lore line. */
        fun addLore(line: String): Builder = apply { lore += line }

        /** Adds or replaces an enchantment by name, for example `sharpness`. */
        fun enchant(enchantment: String, level: Int): Builder = apply {
            require(enchantment.isNotBlank()) { "Enchantment name must not be blank" }
            enchantments[enchantment.trim()] = level
        }

        /** Makes the item a player head with [value] as its texture; `null` removes it. */
        fun texture(value: String?): Builder = apply { texture = value?.trim()?.takeIf(String::isNotEmpty) }

        /** Sets a payload from [ItemService.encode] that restores the item exactly; `null` removes it. */
        fun exact(value: String?): Builder = apply { exact = value?.trim()?.takeIf(String::isNotEmpty) }

        /** Adds a change for anything the spec has no field for; changes run in the order they were added. */
        fun customize(customizer: ItemCustomizer): Builder = apply { customizers += customizer }

        /** Creates the immutable spec. */
        fun build(): ItemSpec = ItemSpec(
            material, amount, name,
            Collections.unmodifiableList(ArrayList(lore)),
            Collections.unmodifiableMap(LinkedHashMap(enchantments)),
            texture, exact,
            Collections.unmodifiableList(ArrayList(customizers)),
        )
    }

    /** Entry point for Java. */
    companion object {
        /** An empty builder: one stone. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

/** Kotlin entry point: `itemSpec { material("APPLE"); amount(3) }`. */
fun itemSpec(block: ItemSpec.Builder.() -> Unit): ItemSpec = ItemSpec.builder().apply(block).build()
