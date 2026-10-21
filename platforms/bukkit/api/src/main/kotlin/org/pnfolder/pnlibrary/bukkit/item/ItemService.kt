package org.pnfolder.pnlibrary.bukkit.item

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack

/**
 * Creates, reads and writes Bukkit items. Obtain it from [org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform.items].
 *
 * Three ways to get an item:
 *
 * - by hand: `items.create(itemSpec { material("APPLE"); name("&aЯблоко") })`;
 * - from configuration in the default format: `items.fromSection(section)`;
 * - from configuration in your own format, with other key names or extra fields: `items.fromSection(section, format)`,
 *   see [ItemFormat].
 *
 * To change a read item before creating it, read the spec first: `items.read(section)?.toBuilder()...`.
 *
 * The default format:
 *
 * ```yaml
 * material: DIAMOND_SWORD
 * amount: 1
 * name: '&bМеч'
 * lore: ['&7Строка']
 * enchantments: { sharpness: 5 }
 * texture: <hash, URL or Base64>   # makes a player head
 * item_data: <Base64>              # exact item, written by writeExact
 * ```
 */
interface ItemService {

    /** Creates a new item from [spec]. */
    fun create(spec: ItemSpec): ItemStack

    /** Reads [section] in the default format; `null` when the section is absent. */
    fun read(section: ConfigurationSection?): ItemSpec?

    /** Reads [section] in [format]; `null` when the section is absent. */
    fun read(section: ConfigurationSection?, format: ItemFormat): ItemSpec?

    /** Reads [values] in the default format; `null` when they are absent. */
    fun read(values: Map<*, *>?): ItemSpec?

    /** Reads [values] in [format]; `null` when they are absent. */
    fun read(values: Map<*, *>?, format: ItemFormat): ItemSpec?

    /** Reads and creates an item from [section] in the default format; `null` when the section is absent. */
    fun fromSection(section: ConfigurationSection?): ItemStack?

    /** Reads and creates an item from [section] in [format]; `null` when the section is absent. */
    fun fromSection(section: ConfigurationSection?, format: ItemFormat): ItemStack?

    /** Reads and creates an item from [values] in the default format; `null` when they are absent. */
    fun fromMap(values: Map<*, *>?): ItemStack?

    /** Reads and creates an item from [values] in [format]; `null` when they are absent. */
    fun fromMap(values: Map<*, *>?, format: ItemFormat): ItemStack?

    /** Replaces [key] in [parent] with a readable description of [item] in the default format. */
    fun write(parent: ConfigurationSection, key: String, item: ItemStack)

    /** Replaces [key] in [parent] with a readable description of [item] in [format]. The item is not changed. */
    fun write(parent: ConfigurationSection, key: String, item: ItemStack, format: ItemFormat)

    /** Like [write], plus a lossless payload that restores the item exactly. */
    fun writeExact(parent: ConfigurationSection, key: String, item: ItemStack)

    /** Like [write] in [format], plus a lossless payload under [ItemFormat.exact]. */
    fun writeExact(parent: ConfigurationSection, key: String, item: ItemStack, format: ItemFormat)

    /** Returns material, amount and a lossless payload, or an empty map for an absent or air item. */
    fun toMap(item: ItemStack?): Map<String, Any>

    /**
     * Creates a player head from a Minecraft texture hash, a `textures.minecraft.net` URL or a Base64 payload
     * (an optional `base64:` prefix is accepted). An unrecognised texture gives a plain head.
     */
    fun head(texture: String?, displayName: String?): ItemStack

    /** Encodes [item] completely as Base64; fails with [IllegalArgumentException] for an unsupported item. */
    fun encode(item: ItemStack): String

    /**
     * Restores an item produced by [encode]; fails with [IllegalArgumentException] for a malformed payload.
     * Decode only trusted data: the format uses Java object deserialization.
     */
    fun decode(encoded: String): ItemStack

    /** Whether [item] is a non-air stack with a positive amount. */
    fun isReal(item: ItemStack?): Boolean
}
