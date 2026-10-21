package org.pnfolder.pnlibrary.bukkit.item

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import java.util.Locale

/** Bukkit item service. Reading builds an [ItemSpec], so read and hand-made items are created by the same code. */
internal object ItemServiceImpl : ItemService {
    override fun create(spec: ItemSpec): ItemStack {
        val item = decodeOrNull(spec.exact) ?: spec.texture?.let { texture ->
            PlayerHeads.create(withoutPrefix(texture), spec.name ?: "&fItem").apply { amount = spec.amount }
        } ?: ItemStack(Material.matchMaterial(spec.material) ?: Material.STONE, spec.amount)
        val meta = item.itemMeta
        if (meta != null) {
            if (!spec.name.isNullOrBlank()) meta.setDisplayName(ItemText.color(spec.name))
            if (spec.lore.isNotEmpty()) meta.lore = spec.lore.map { ItemText.color(it) }
            item.itemMeta = meta
        }
        for ((key, level) in spec.enchantments) {
            val normalized = key.uppercase(Locale.ROOT).removePrefix("MINECRAFT:")
            Enchantment.getByName(normalized)?.let { item.addUnsafeEnchantment(it, level) }
        }
        spec.customizers.forEach { it.apply(item) }
        return item
    }

    override fun read(section: ConfigurationSection?): ItemSpec? = read(section, ItemFormat.DEFAULT)

    override fun read(section: ConfigurationSection?, format: ItemFormat): ItemSpec? = read(section?.getValues(false), format)

    override fun read(values: Map<*, *>?): ItemSpec? = read(values, ItemFormat.DEFAULT)

    override fun read(values: Map<*, *>?, format: ItemFormat): ItemSpec? {
        if (values == null) return null
        val spec = ItemSpec.builder()
        values[format.material]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { material ->
            if (hasBase64Prefix(material)) spec.texture(material) else spec.material(material)
        }
        format.textures.firstNotNullOfOrNull { key -> values[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
            ?.let(spec::texture)
        spec.amount(integer(values[format.amount]).coerceAtLeast(1))
        spec.name(values[format.name]?.toString())
        when (val lore = values[format.lore]) {
            is List<*> -> spec.lore(lore.filterIsInstance<String>())
            is String -> spec.lore(lore)
        }
        val enchantments = when (val raw = values[format.enchantments]) {
            is ConfigurationSection -> raw.getValues(false)
            is Map<*, *> -> raw
            else -> emptyMap<Any, Any>()
        }
        enchantments.forEach { (key, level) -> spec.enchant(key.toString(), integer(level)) }
        spec.exact(values[format.exact]?.toString())
        format.fields.forEach { (key, field) -> values[key]?.let { field.read(spec, it) } }
        return spec.build()
    }

    override fun fromSection(section: ConfigurationSection?): ItemStack? = read(section)?.let(::create)

    override fun fromSection(section: ConfigurationSection?, format: ItemFormat): ItemStack? = read(section, format)?.let(::create)

    override fun fromMap(values: Map<*, *>?): ItemStack? = read(values)?.let(::create)

    override fun fromMap(values: Map<*, *>?, format: ItemFormat): ItemStack? = read(values, format)?.let(::create)

    override fun write(parent: ConfigurationSection, key: String, item: ItemStack) = write(parent, key, item, ItemFormat.DEFAULT)

    override fun write(parent: ConfigurationSection, key: String, item: ItemStack, format: ItemFormat) {
        require(key.isNotBlank()) { "Item key must not be blank" }
        require(isReal(item)) { "Source must be a real item" }
        val copy = item.clone()
        parent.set(key, null)
        val section = parent.createSection(key)
        section.set(format.material, copy.type.name)
        if (copy.amount > 1) section.set(format.amount, copy.amount)
        val meta = copy.itemMeta ?: return
        if (meta.hasDisplayName()) section.set(format.name, meta.displayName)
        if (meta.hasLore() && !meta.lore.isNullOrEmpty()) section.set(format.lore, meta.lore)
        if (meta.enchants.isNotEmpty()) {
            val enchantments = section.createSection(format.enchantments)
            meta.enchants.entries.sortedBy { it.key.name.lowercase(Locale.ROOT) }.forEach {
                enchantments.set(it.key.name.lowercase(Locale.ROOT), it.value)
            }
        }
    }

    override fun writeExact(parent: ConfigurationSection, key: String, item: ItemStack) =
        writeExact(parent, key, item, ItemFormat.DEFAULT)

    override fun writeExact(parent: ConfigurationSection, key: String, item: ItemStack, format: ItemFormat) {
        require(key.isNotBlank()) { "Item key must not be blank" }
        require(isReal(item)) { "Source must be a real item" }
        val copy = item.clone()
        val encoded = ItemStackCodec.encode(copy)
        write(parent, key, copy, format)
        parent.getConfigurationSection(key).set(format.exact, encoded)
    }

    override fun toMap(item: ItemStack?): Map<String, Any> {
        if (item == null || !isReal(item)) return emptyMap()
        val copy = item.clone()
        return linkedMapOf<String, Any>("material" to copy.type.name).apply {
            if (copy.amount > 1) put("amount", copy.amount)
            put("item_data", ItemStackCodec.encode(copy))
        }
    }

    override fun head(texture: String?, displayName: String?): ItemStack = PlayerHeads.create(texture, displayName)

    override fun encode(item: ItemStack): String = ItemStackCodec.encode(item)

    override fun decode(encoded: String): ItemStack = ItemStackCodec.decode(encoded)

    override fun isReal(item: ItemStack?): Boolean = item != null && item.amount > 0 &&
        item.type.name != "AIR" && !item.type.name.endsWith("_AIR")

    private fun hasBase64Prefix(value: String) = value.startsWith("base64-", true) || value.startsWith("base64:", true)

    private fun withoutPrefix(value: String): String = if (hasBase64Prefix(value)) value.substring(7).trim() else value

    private fun decodeOrNull(value: String?): ItemStack? {
        if (value.isNullOrBlank()) return null
        return try {
            ItemStackCodec.decode(value).takeIf(::isReal)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun integer(value: Any?): Int = (value as? Number)?.toInt() ?: value?.toString()?.toIntOrNull() ?: 1
}
