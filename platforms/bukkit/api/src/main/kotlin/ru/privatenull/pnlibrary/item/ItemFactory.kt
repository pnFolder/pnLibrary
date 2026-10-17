package ru.privatenull.pnlibrary.item

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import java.util.Locale

/** Reads and writes Bukkit items using the same configuration format for sections and maps. */
object ItemFactory {
    /** Reads material, amount, name, lore, enchantments, texture and optional item_data. */
    @JvmStatic
    fun fromSection(section: ConfigurationSection?): ItemStack? = fromMap(section?.getValues(false))

    /**
     * Reads an item, preferring a valid item_data payload over material and texture.
     * Explicit name, lore and enchantments customize both decoded and newly created items.
     * A missing configuration returns null; an unknown material falls back to stone.
     */
    @JvmStatic
    fun fromMap(values: Map<*, *>?): ItemStack? {
        if (values == null) return null
        val name = values["name"]?.toString()
        val item = decode(values["item_data"]?.toString()) ?: run {
            val texture = firstTexture(values)
            val amount = integer(values["amount"]).coerceAtLeast(1)
            if (texture == null) {
                val materialName = values["material"]?.toString()?.takeIf { it.isNotBlank() } ?: "STONE"
                ItemStack(Material.matchMaterial(materialName) ?: Material.STONE, amount)
            } else {
                HeadUtil.create(texture, name ?: "&fItem").apply { this.amount = amount }
            }
        }
        val meta = item.itemMeta
        if (meta != null) {
            if (!name.isNullOrBlank()) meta.setDisplayName(ItemText.color(name))
            val lore = (values["lore"] as? List<*>)?.filterIsInstance<String>()
            if (!lore.isNullOrEmpty()) meta.lore = lore.map { ItemText.color(it) }
            item.itemMeta = meta
        }
        val enchantments = when (val raw = values["enchantments"]) {
            is ConfigurationSection -> raw.getValues(false)
            is Map<*, *> -> raw
            else -> emptyMap<Any, Any>()
        }
        for ((key, level) in enchantments) {
            val normalized = key.toString().uppercase(Locale.ROOT).removePrefix("MINECRAFT:")
            Enchantment.getByName(normalized)?.let { item.addUnsafeEnchantment(it, integer(level)) }
        }
        return item
    }

    /** Replaces [key] with a readable item description. The source item is not modified. */
    @JvmStatic
    fun writeItem(parent: ConfigurationSection?, key: String?, source: ItemStack?) {
        requireNotNull(parent) { "Parent section must not be null" }
        require(!key.isNullOrBlank()) { "Item key must not be blank" }
        require(isRealItem(source)) { "Source must be a real item" }
        val item = requireNotNull(source).clone()
        parent.set(key, null)
        val section = parent.createSection(key)
        section.set("material", item.type.name)
        if (item.amount > 1) section.set("amount", item.amount)
        val meta = item.itemMeta ?: return
        if (meta.hasDisplayName()) section.set("name", meta.displayName)
        if (meta.hasLore() && !meta.lore.isNullOrEmpty()) section.set("lore", meta.lore)
        if (meta.enchants.isNotEmpty()) {
            val enchantments = section.createSection("enchantments")
            meta.enchants.entries.sortedBy { it.key.name.lowercase(Locale.ROOT) }.forEach {
                enchantments.set(it.key.name.lowercase(Locale.ROOT), it.value)
            }
        }
    }

    /** Writes readable fields plus a lossless payload, including the original stack amount. */
    @JvmStatic
    fun writeExactItem(parent: ConfigurationSection?, key: String?, source: ItemStack?) {
        requireNotNull(parent) { "Parent section must not be null" }
        require(!key.isNullOrBlank()) { "Item key must not be blank" }
        require(isRealItem(source)) { "Source must be a real item" }
        val item = requireNotNull(source).clone()
        val encoded = ItemStackCodec.encode(item)
        writeItem(parent, key, item)
        parent.getConfigurationSection(key).set("item_data", encoded)
    }

    /** Returns material, amount and a lossless payload, or an empty map for absent/air items. */
    @JvmStatic
    fun toMap(source: ItemStack?): Map<String, Any> {
        if (!isRealItem(source)) return emptyMap()
        val item = requireNotNull(source).clone()
        return linkedMapOf<String, Any>("material" to item.type.name).apply {
            if (item.amount > 1) put("amount", item.amount)
            put("item_data", ItemStackCodec.encode(item))
        }
    }

    /** True for a non-air stack with a positive amount. */
    @JvmStatic
    fun isRealItem(item: ItemStack?): Boolean = item != null && item.amount > 0 &&
        item.type.name != "AIR" && !item.type.name.endsWith("_AIR")

    private fun firstTexture(values: Map<*, *>): String? =
        normalize(values["base64"]?.toString()) ?: normalize(values["texture"]?.toString()) ?:
        values["material"]?.toString()?.trim()?.takeIf {
            it.startsWith("base64-", true) || it.startsWith("base64:", true)
        }?.let(::normalize)

    private fun normalize(value: String?): String? {
        val trimmed = value?.trim() ?: return null
        val result = if (trimmed.startsWith("base64-", true) || trimmed.startsWith("base64:", true)) {
            trimmed.substring(7).trim()
        } else trimmed
        return result.takeIf { it.isNotEmpty() }
    }

    private fun decode(value: String?): ItemStack? {
        if (value.isNullOrBlank()) return null
        return try {
            ItemStackCodec.decode(value).takeIf(::isRealItem)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun integer(value: Any?): Int = (value as? Number)?.toInt() ?: value?.toString()?.toIntOrNull() ?: 1
}
