package org.pnfolder.pnlibrary.bukkit.localization.internal

import org.bukkit.DyeColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Biome
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.potion.PotionEffectType
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.localization.TranslatableType
import org.pnfolder.pnlibrary.localization.TranslationCategory
import org.pnfolder.pnlibrary.localization.TranslationIndex
import org.pnfolder.pnlibrary.localization.TranslationMatch
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts
import java.util.Locale

// --- Forward lookup: Bukkit value -> candidate translation keys ---
// keysOf returns modern (1.13+) keys first, then pre-1.13 legacy keys; translate() picks the first the
// loaded table actually contains, so the version-correct key wins without the version being passed in.

/**
 * Translation keys of a [Material], block and item variants.
 */
internal object MaterialTranslatable : TranslatableType<Material> {
    override fun keysOf(value: Material): List<String> {
        val path = value.name.lowercase(Locale.ROOT)
        val modern = if (value.isBlock) listOf(TranslationCategory.BLOCK, TranslationCategory.ITEM)
        else listOf(TranslationCategory.ITEM, TranslationCategory.BLOCK)
        // Pre-1.13 blocks/items are best-effort: many used camelCase names or data-value variants and fall back.
        val camel = camelCase(value.name)
        val legacy = listOf("tile.$camel.name", "item.$camel.name")
        return (listOfNotNull(translationKey(value)) + modern.map { it.key(path) } + legacy).distinct()
    }

    override fun fallbackName(value: Material): String = value.name.lowercase(Locale.ROOT).replace('_', ' ')
}

/** Translation keys of an [Enchantment]: the modern key, then the pre-1.13 one. */
internal object EnchantmentTranslatable : TranslatableType<Enchantment> {
    override fun keysOf(value: Enchantment): List<String> {
        val modern = namespacedKey(value)?.substringAfter(':')
            ?: legacyName(value).uppercase(Locale.ROOT).let { ENCHANTMENT_LEGACY_TO_MODERN[it] ?: it }.lowercase(Locale.ROOT)
        val legacy = ENCHANTMENT_LEGACY[modern]?.let { "enchantment.$it" }
        return (listOf(TranslationCategory.ENCHANTMENT.key(modern)) + listOfNotNull(legacy)).distinct()
    }

    override fun fallbackName(value: Enchantment): String = legacyName(value).lowercase(Locale.ROOT).replace('_', ' ')
}

/** Translation keys of an [EntityType]: the modern key, then the pre-1.13 one. */
internal object EntityTranslatable : TranslatableType<EntityType> {
    override fun keysOf(value: EntityType): List<String> {
        val path = namespacedKey(value)?.substringAfter(':') ?: value.name.lowercase(Locale.ROOT)
        // Pre-1.13 entity keys are best-effort: capitalized names, a few historical spellings mapped below.
        val pascal = ENTITY_LEGACY[path] ?: pascalCase(path)
        return listOf(TranslationCategory.ENTITY.key(path), "entity.$pascal.name").distinct()
    }

    override fun fallbackName(value: EntityType): String = value.name.lowercase(Locale.ROOT).replace('_', ' ')
}

/** Translation keys of a [PotionEffectType]: the modern key, then the pre-1.13 one. */
internal object EffectTranslatable : TranslatableType<PotionEffectType> {
    override fun keysOf(value: PotionEffectType): List<String> {
        val modern = namespacedKey(value)?.substringAfter(':')
            ?: effectName(value).uppercase(Locale.ROOT).let { EFFECT_LEGACY_TO_MODERN[it] ?: it }.lowercase(Locale.ROOT)
        val legacy = EFFECT_LEGACY[modern]?.let { "effect.$it" }
        return (listOf(TranslationCategory.EFFECT.key(modern)) + listOfNotNull(legacy)).distinct()
    }

    override fun fallbackName(value: PotionEffectType): String = effectName(value).lowercase(Locale.ROOT).replace('_', ' ')
}

/** Translation key of a [Biome]. */
internal object BiomeTranslatable : TranslatableType<Biome> {
    override fun keysOf(value: Biome): List<String> {
        val path = namespacedKey(value)?.substringAfter(':') ?: value.name.lowercase(Locale.ROOT)
        return listOf(TranslationCategory.BIOME.key(path))
    }

    override fun fallbackName(value: Biome): String = value.name.lowercase(Locale.ROOT).replace('_', ' ')
}

/** Translation key of a [DyeColor]. */
internal object ColorTranslatable : TranslatableType<DyeColor> {
    override fun keysOf(value: DyeColor): List<String> = listOf(TranslationCategory.COLOR.key(value.name.lowercase(Locale.ROOT)))
    override fun fallbackName(value: DyeColor): String = value.name.lowercase(Locale.ROOT).replace('_', ' ')
}

/** Translation key of a [GameMode]. */
internal object GameModeTranslatable : TranslatableType<GameMode> {
    override fun keysOf(value: GameMode): List<String> = listOf(TranslationCategory.GAME_MODE.key(value.name.lowercase(Locale.ROOT)))
    override fun fallbackName(value: GameMode): String = value.name.lowercase(Locale.ROOT).replace('_', ' ')
}

// --- Reverse lookup: translated text -> Bukkit value ---

/** Builds an index of the entries of [values] whose key starts with one of [prefixes]; [resolve] maps the last key segment to a value. */
private fun <T> reverseIndex(
    values: Map<String, String>,
    layouts: KeyboardLayouts,
    prefixes: List<String>,
    resolve: (String) -> T?,
): TranslationIndex<T> = TranslationIndex.of(
    values.asSequence()
        .filter { (key, _) -> prefixes.any(key::startsWith) }
        .map { (key, value) -> TranslationMatch(key, value, resolve(key.substringAfterLast('.').uppercase(Locale.ROOT))) }
        .toList(),
    layouts,
)

private fun prefixOf(category: TranslationCategory) = listOf(category.prefix())

/** Reverse index of item and block names. */
internal fun bukkitMaterials(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<Material> =
    reverseIndex(values, layouts, listOf(TranslationCategory.ITEM.prefix(), TranslationCategory.BLOCK.prefix()), { Material.matchMaterial(it) })

/** Reverse index of enchantment names. */
internal fun bukkitEnchantments(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<Enchantment> =
    reverseIndex(values, layouts, prefixOf(TranslationCategory.ENCHANTMENT), ::enchantmentOf)

/** Reverse index of entity names. */
internal fun bukkitEntities(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<EntityType> =
    reverseIndex(values, layouts, prefixOf(TranslationCategory.ENTITY), ::entityOf)

/** Reverse index of potion effect names. */
internal fun bukkitEffects(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<PotionEffectType> =
    reverseIndex(values, layouts, prefixOf(TranslationCategory.EFFECT), ::effectOf)

/** Reverse index of biome names. */
internal fun bukkitBiomes(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<Biome> =
    reverseIndex(values, layouts, prefixOf(TranslationCategory.BIOME), ::biomeOf)

/** Reverse index of dye color names. */
internal fun bukkitColors(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<DyeColor> =
    reverseIndex(values, layouts, prefixOf(TranslationCategory.COLOR), ::colorOf)

/** Reverse index of game mode names. */
internal fun bukkitGameModes(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<GameMode> =
    reverseIndex(values, layouts, prefixOf(TranslationCategory.GAME_MODE), ::gameModeOf)

/** Enchantment for a modern constant name, or null; tries the legacy alias and the namespaced key. */
private fun enchantmentOf(modernName: String): Enchantment? {
    val legacy = ENCHANTMENT_ALIASES[modernName] ?: modernName
    val constant = staticField(Enchantment::class.java, legacy) as? Enchantment
    return constant ?: Enchantment.values().firstOrNull {
        it.name.equals(legacy, true) || namespacedKey(it)?.equals(modernName.lowercase(Locale.ROOT), true) == true
    }
}

/** Entity type whose constant or namespaced key equals [path], or null. */
private fun entityOf(path: String): EntityType? = EntityType.values().firstOrNull {
    it.name.equals(path, true) || namespacedKey(it)?.equals(path.lowercase(Locale.ROOT), true) == true
}

/** Potion effect for a modern constant name, or null; tries the legacy alias and the namespaced key. */
private fun effectOf(modernName: String): PotionEffectType? {
    val legacy = EFFECT_ALIASES[modernName] ?: modernName
    val constant = staticField(PotionEffectType::class.java, legacy) as? PotionEffectType
    return constant ?: PotionEffectType.values().firstOrNull {
        effectName(it).equals(legacy, true) || namespacedKey(it)?.equals(modernName.lowercase(Locale.ROOT), true) == true
    }
}

/** Biome whose constant or namespaced key equals [path], or null. */
private fun biomeOf(path: String): Biome? = Biome.values().firstOrNull {
    it.name.equals(path, true) || namespacedKey(it)?.equals(path.lowercase(Locale.ROOT), true) == true
}

/** Dye color named [path], or null. */
private fun colorOf(path: String): DyeColor? = DyeColor.values().firstOrNull { it.name.equals(path, true) }

/** Game mode named [path], or null. */
private fun gameModeOf(path: String): GameMode? = GameMode.values().firstOrNull { it.name.equals(path, true) }

// --- Reflection kept out of the public API files (enforced by SourceConventionsTest) ---

/** Exact key via `org.bukkit.Translatable`, absent on legacy servers. */
private fun translationKey(value: Any): String? = try {
    value.javaClass.methods.firstOrNull { it.name == "getTranslationKey" && it.parameterTypes.isEmpty() }
        ?.invoke(value) as? String
} catch (_: ReflectiveOperationException) {
    null
}

/** Namespaced key (e.g. `minecraft:sharpness`) via `getKey().getKey()`, absent on legacy servers. */
private fun namespacedKey(value: Any): String? = try {
    val key = value.javaClass.methods.firstOrNull { it.name == "getKey" && it.parameterTypes.isEmpty() }
        ?.invoke(value) ?: return null
    key.javaClass.methods.firstOrNull { it.name == "getKey" && it.parameterTypes.isEmpty() }
        ?.invoke(key)?.toString()
} catch (_: ReflectiveOperationException) {
    null
}

/** Value of the public static field named [name] on [type], or null. */
private fun staticField(type: Class<*>, name: String): Any? =
    type.fields.firstOrNull { it.name == name }?.get(null)

/** Name of the public static field on [type] holding [value], or null. */
private fun staticFieldName(type: Class<*>, value: Any): String? =
    type.fields.firstOrNull { it.get(null) === value }?.name

/** Legacy constant name (e.g. `DAMAGE_ALL`), read from the static field to avoid `getName()` on unbound wrappers. */
private fun legacyName(enchantment: Enchantment): String =
    staticFieldName(Enchantment::class.java, enchantment)
        ?: runCatchingNonFatal { @Suppress("DEPRECATION") enchantment.name }.getOrNull()
        ?: "unknown"

/** Legacy constant name (e.g. `FAST_DIGGING`), read from the static field to avoid `getName()` on unbound wrappers. */
private fun effectName(effect: PotionEffectType): String =
    staticFieldName(PotionEffectType::class.java, effect)
        ?: runCatchingNonFatal { effect.name }.getOrNull()
        ?: "unknown"

/** `DIAMOND_SWORD` -> `diamondSword`, the shape of a pre-1.13 tile/item key fragment. */
private fun camelCase(name: String): String = name.lowercase(Locale.ROOT).split('_')
    .mapIndexed { index, word -> if (index == 0) word else word.replaceFirstChar(Char::uppercase) }
    .joinToString("")

/** `ender_dragon` -> `EnderDragon`, the shape of a pre-1.13 entity key fragment. */
private fun pascalCase(path: String): String = path.lowercase(Locale.ROOT).split('_')
    .joinToString("") { word -> word.replaceFirstChar(Char::uppercase) }
