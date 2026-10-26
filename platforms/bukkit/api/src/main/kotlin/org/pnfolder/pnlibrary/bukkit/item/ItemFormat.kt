package org.pnfolder.pnlibrary.bukkit.item

import java.util.Collections

/** Reads one extra configuration field into the spec being built, usually through [ItemSpec.Builder.customize]. */
fun interface ItemField {
    /** Applies the raw configuration [value] (a string, number, list, map or section) to [item]. */
    fun read(item: ItemSpec.Builder, value: Any)
}

/**
 * Which configuration keys [ItemService.read] uses for each item field, plus fields of your own.
 *
 * ```kotlin
 * val format = itemFormat {
 *     material("type")
 *     name("display-name")
 *     lore("description")
 *     field("glow") { item, value -> if (value == true) item.enchant("unbreaking", 1) }
 *     field("model") { item, value ->
 *         item.customize { stack -> stack.itemMeta = stack.itemMeta?.apply { setCustomModelData(value.toString().toInt()) } }
 *     }
 * }
 * val reward = items.fromSection(config.getConfigurationSection("reward"), format)
 * ```
 *
 * Keys not listed keep their default names. A `material` written as `base64:<texture>` is read as a texture.
 *
 * @property material key of the material
 * @property amount key of the stack size
 * @property name key of the display name
 * @property lore key of the lore lines
 * @property enchantments key of the enchantments
 * @property textures keys that may hold a head texture
 * @property exact key of the exact serialized item data
 * @property fields extra fields read by key
 */
class ItemFormat private constructor(
    val material: String,
    val amount: String,
    val name: String,
    val lore: String,
    val enchantments: String,
    val textures: List<String>,
    val exact: String,
    val fields: Map<String, ItemField>,
) {
    /** A builder holding this format's keys, to change a few of them. */
    fun toBuilder(): Builder = Builder().also { builder ->
        builder.material(material).amount(amount).name(name).lore(lore).enchantments(enchantments)
            .texture(*textures.toTypedArray()).exact(exact)
        fields.forEach { (key, field) -> builder.field(key, field) }
    }

    /** Fluent builder; every method returns the same builder. */
    class Builder internal constructor() {
        private var material = "material"
        private var amount = "amount"
        private var name = "name"
        private var lore = "lore"
        private var enchantments = "enchantments"
        private var textures = listOf("base64", "texture")
        private var exact = "item_data"
        private val fields = linkedMapOf<String, ItemField>()

        /** Key of the material. Default `material`. */
        fun material(key: String): Builder = apply { material = key(key) }

        /** Key of the stack size. Default `amount`. */
        fun amount(key: String): Builder = apply { amount = key(key) }

        /** Key of the display name. Default `name`. */
        fun name(key: String): Builder = apply { name = key(key) }

        /** Key of the lore: a list of lines or a single line. Default `lore`. */
        fun lore(key: String): Builder = apply { lore = key(key) }

        /** Key of the enchantments section, `name: level`. Default `enchantments`. */
        fun enchantments(key: String): Builder = apply { enchantments = key(key) }

        /** Keys of the head texture, the first present one wins. Default `base64`, then `texture`. */
        fun texture(vararg keys: String): Builder = apply {
            require(keys.isNotEmpty()) { "At least one texture key is required" }
            textures = keys.map(::key)
        }

        /** Key of the lossless payload written by [ItemService.writeExact]. Default `item_data`. */
        fun exact(key: String): Builder = apply { exact = key(key) }

        /** Reads a field of your own from [key]; it runs after the standard fields, in the order added. */
        fun field(key: String, field: ItemField): Builder = apply { fields[key(key)] = field }

        /** Creates the immutable format. */
        fun build(): ItemFormat = ItemFormat(
            material, amount, name, lore, enchantments, textures, exact,
            Collections.unmodifiableMap(LinkedHashMap(fields)),
        )

        private fun key(value: String): String {
            require(value.isNotBlank()) { "Configuration key must not be blank" }
            return value.trim()
        }
    }

    /** The default format and the builder entry point. */
    companion object {
        /** `material`, `amount`, `name`, `lore`, `enchantments`, `base64`/`texture` and `item_data`. */
        @JvmField
        val DEFAULT: ItemFormat = Builder().build()

        /** A builder starting from the default keys. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

/** Kotlin entry point: `itemFormat { name("display-name") }`. */
fun itemFormat(block: ItemFormat.Builder.() -> Unit): ItemFormat = ItemFormat.builder().apply(block).build()
