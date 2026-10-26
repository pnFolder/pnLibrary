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

internal object EnchantmentTranslatable : TranslatableType<Enchantment> {
    override fun keysOf(value: Enchantment): List<String> {
        val modern = namespacedKey(value)?.substringAfter(':')
            ?: legacyName(value).uppercase(Locale.ROOT).let { ENCHANTMENT_LEGACY_TO_MODERN[it] ?: it }.lowercase(Locale.ROOT)
        val legacy = ENCHANTMENT_LEGACY[modern]?.let { "enchantment.$it" }
        return (listOf(TranslationCategory.ENCHANTMENT.key(modern)) + listOfNotNull(legacy)).distinct()
    }

    override fun fallbackName(value: Enchantment): String = legacyName(value).lowercase(Locale.ROOT).replace('_', ' ')
}

internal object EntityTranslatable : TranslatableType<EntityType> {
    override fun keysOf(value: EntityType): List<String> {
        val path = namespacedKey(value)?.substringAfter(':') ?: value.name.lowercase(Locale.ROOT)
        // Pre-1.13 entity keys are best-effort: capitalized names, a few historical spellings mapped below.
        val pascal = ENTITY_LEGACY[path] ?: pascalCase(path)
        return listOf(TranslationCategory.ENTITY.key(path), "entity.$pascal.name").distinct()
    }

    override fun fallbackName(value: EntityType): String = value.name.lowercase(Locale.ROOT).replace('_', ' ')
}

internal object EffectTranslatable : TranslatableType<PotionEffectType> {
    override fun keysOf(value: PotionEffectType): List<String> {
        val modern = namespacedKey(value)?.substringAfter(':')
            ?: effectName(value).uppercase(Locale.ROOT).let { EFFECT_LEGACY_TO_MODERN[it] ?: it }.lowercase(Locale.ROOT)
        val legacy = EFFECT_LEGACY[modern]?.let { "effect.$it" }
        return (listOf(TranslationCategory.EFFECT.key(modern)) + listOfNotNull(legacy)).distinct()
    }

    override fun fallbackName(value: PotionEffectType): String = effectName(value).lowercase(Locale.ROOT).replace('_', ' ')
}

internal object BiomeTranslatable : TranslatableType<Biome> {
    override fun keysOf(value: Biome): List<String> {
        val path = namespacedKey(value)?.substringAfter(':') ?: value.name.lowercase(Locale.ROOT)
        return listOf(TranslationCategory.BIOME.key(path))
    }

    override fun fallbackName(value: Biome): String = value.name.lowercase(Locale.ROOT).replace('_', ' ')
}

internal object ColorTranslatable : TranslatableType<DyeColor> {
    override fun keysOf(value: DyeColor): List<String> = listOf(TranslationCategory.COLOR.key(value.name.lowercase(Locale.ROOT)))
    override fun fallbackName(value: DyeColor): String = value.name.lowercase(Locale.ROOT).replace('_', ' ')
}

internal object GameModeTranslatable : TranslatableType<GameMode> {
    override fun keysOf(value: GameMode): List<String> = listOf(TranslationCategory.GAME_MODE.key(value.name.lowercase(Locale.ROOT)))
    override fun fallbackName(value: GameMode): String = value.name.lowercase(Locale.ROOT).replace('_', ' ')
}

// --- Reverse lookup: translated text -> Bukkit value ---

internal fun bukkitMaterials(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<Material> = TranslationIndex.of(
    values.asSequence()
        .filter { (key, _) -> key.startsWith(TranslationCategory.ITEM.prefix()) || key.startsWith(TranslationCategory.BLOCK.prefix()) }
        .map { (key, value) ->
            TranslationMatch(key, value, Material.matchMaterial(key.substringAfterLast('.').uppercase(Locale.ROOT)))
        }
        .toList(),
    layouts,
)

internal fun bukkitEnchantments(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<Enchantment> = TranslationIndex.of(
    values.asSequence()
        .filter { (key, _) -> key.startsWith(TranslationCategory.ENCHANTMENT.prefix()) }
        .map { (key, value) -> TranslationMatch(key, value, enchantmentOf(key.substringAfterLast('.').uppercase(Locale.ROOT))) }
        .toList(),
    layouts,
)

internal fun bukkitEntities(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<EntityType> = TranslationIndex.of(
    values.asSequence()
        .filter { (key, _) -> key.startsWith(TranslationCategory.ENTITY.prefix()) }
        .map { (key, value) -> TranslationMatch(key, value, entityOf(key.substringAfterLast('.').uppercase(Locale.ROOT))) }
        .toList(),
    layouts,
)

internal fun bukkitEffects(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<PotionEffectType> = TranslationIndex.of(
    values.asSequence()
        .filter { (key, _) -> key.startsWith(TranslationCategory.EFFECT.prefix()) }
        .map { (key, value) -> TranslationMatch(key, value, effectOf(key.substringAfterLast('.').uppercase(Locale.ROOT))) }
        .toList(),
    layouts,
)

internal fun bukkitBiomes(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<Biome> = TranslationIndex.of(
    values.asSequence()
        .filter { (key, _) -> key.startsWith(TranslationCategory.BIOME.prefix()) }
        .map { (key, value) -> TranslationMatch(key, value, biomeOf(key.substringAfterLast('.').uppercase(Locale.ROOT))) }
        .toList(),
    layouts,
)

internal fun bukkitColors(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<DyeColor> = TranslationIndex.of(
    values.asSequence()
        .filter { (key, _) -> key.startsWith(TranslationCategory.COLOR.prefix()) }
        .map { (key, value) -> TranslationMatch(key, value, colorOf(key.substringAfterLast('.').uppercase(Locale.ROOT))) }
        .toList(),
    layouts,
)

internal fun bukkitGameModes(values: Map<String, String>, layouts: KeyboardLayouts): TranslationIndex<GameMode> = TranslationIndex.of(
    values.asSequence()
        .filter { (key, _) -> key.startsWith(TranslationCategory.GAME_MODE.prefix()) }
        .map { (key, value) -> TranslationMatch(key, value, gameModeOf(key.substringAfterLast('.').uppercase(Locale.ROOT))) }
        .toList(),
    layouts,
)

private fun enchantmentOf(modernName: String): Enchantment? {
    val legacy = ENCHANTMENT_ALIASES[modernName] ?: modernName
    val constant = staticField(Enchantment::class.java, legacy) as? Enchantment
    return constant ?: Enchantment.values().firstOrNull {
        it.name.equals(legacy, true) || namespacedKey(it)?.equals(modernName.lowercase(Locale.ROOT), true) == true
    }
}

private fun entityOf(path: String): EntityType? = EntityType.values().firstOrNull {
    it.name.equals(path, true) || namespacedKey(it)?.equals(path.lowercase(Locale.ROOT), true) == true
}

private fun effectOf(modernName: String): PotionEffectType? {
    val legacy = EFFECT_ALIASES[modernName] ?: modernName
    val constant = staticField(PotionEffectType::class.java, legacy) as? PotionEffectType
    return constant ?: PotionEffectType.values().firstOrNull {
        effectName(it).equals(legacy, true) || namespacedKey(it)?.equals(modernName.lowercase(Locale.ROOT), true) == true
    }
}

private fun biomeOf(path: String): Biome? = Biome.values().firstOrNull {
    it.name.equals(path, true) || namespacedKey(it)?.equals(path.lowercase(Locale.ROOT), true) == true
}

private fun colorOf(path: String): DyeColor? = DyeColor.values().firstOrNull { it.name.equals(path, true) }

private fun gameModeOf(path: String): GameMode? = GameMode.values().firstOrNull { it.name.equals(path, true) }

// --- Reflection kept out of the public API files (enforced by SourceConventionsTest) ---

/** Exact key via [org.bukkit.Translatable], absent on legacy servers. */
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

private val ENCHANTMENT_ALIASES = mapOf(
    "PROTECTION" to "PROTECTION_ENVIRONMENTAL", "FIRE_PROTECTION" to "PROTECTION_FIRE",
    "FEATHER_FALLING" to "PROTECTION_FALL", "BLAST_PROTECTION" to "PROTECTION_EXPLOSIONS",
    "PROJECTILE_PROTECTION" to "PROTECTION_PROJECTILE", "RESPIRATION" to "OXYGEN",
    "AQUA_AFFINITY" to "WATER_WORKER", "SHARPNESS" to "DAMAGE_ALL",
    "SMITE" to "DAMAGE_UNDEAD", "BANE_OF_ARTHROPODS" to "DAMAGE_ARTHROPODS",
    "LOOTING" to "LOOT_BONUS_MOBS", "EFFICIENCY" to "DIG_SPEED",
    "UNBREAKING" to "DURABILITY", "FORTUNE" to "LOOT_BONUS_BLOCKS",
    "POWER" to "ARROW_DAMAGE", "PUNCH" to "ARROW_KNOCKBACK",
    "FLAME" to "ARROW_FIRE", "INFINITY" to "ARROW_INFINITE",
)

private val ENCHANTMENT_LEGACY_TO_MODERN: Map<String, String> =
    ENCHANTMENT_ALIASES.entries.associate { (modern, legacy) -> legacy to modern }

private val EFFECT_ALIASES = mapOf(
    "SLOWNESS" to "SLOW", "HASTE" to "FAST_DIGGING", "MINING_FATIGUE" to "SLOW_DIGGING",
    "STRENGTH" to "INCREASE_DAMAGE", "INSTANT_HEALTH" to "HEAL", "INSTANT_DAMAGE" to "HARM",
    "JUMP_BOOST" to "JUMP", "NAUSEA" to "CONFUSION", "RESISTANCE" to "DAMAGE_RESISTANCE",
)

private val EFFECT_LEGACY_TO_MODERN: Map<String, String> =
    EFFECT_ALIASES.entries.associate { (modern, legacy) -> legacy to modern }

// Pre-1.13 key fragments, keyed by the modern vanilla path so they are independent of Bukkit's constant renames.
private val ENCHANTMENT_LEGACY = mapOf(
    "sharpness" to "damage.all", "smite" to "damage.undead", "bane_of_arthropods" to "damage.arthropods",
    "protection" to "protect.all", "fire_protection" to "protect.fire", "feather_falling" to "protect.fall",
    "blast_protection" to "protect.explosion", "projectile_protection" to "protect.projectile",
    "respiration" to "oxygen", "aqua_affinity" to "waterWorker", "thorns" to "thorns",
    "depth_strider" to "waterWalker", "frost_walker" to "frostWalker", "binding_curse" to "binding_curse",
    "knockback" to "knockback", "fire_aspect" to "fire", "looting" to "lootBonus",
    "sweeping" to "sweeping", "sweeping_edge" to "sweeping", "efficiency" to "digging",
    "silk_touch" to "untouching", "unbreaking" to "durability", "fortune" to "lootBonusDigger",
    "luck_of_the_sea" to "lootBonusFishing", "lure" to "fishingSpeed", "power" to "arrowDamage",
    "punch" to "arrowKnockback", "flame" to "arrowFire", "infinity" to "arrowInfinite",
    "mending" to "mending", "vanishing_curse" to "vanishing_curse",
)

private val EFFECT_LEGACY = mapOf(
    "speed" to "moveSpeed", "slowness" to "moveSlowdown", "haste" to "digSpeed", "mining_fatigue" to "digSlowDown",
    "strength" to "damageBoost", "instant_health" to "heal", "instant_damage" to "harm", "jump_boost" to "jump",
    "nausea" to "confusion", "regeneration" to "regeneration", "resistance" to "resistance",
    "fire_resistance" to "fireResistance", "water_breathing" to "waterBreathing", "invisibility" to "invisibility",
    "blindness" to "blindness", "night_vision" to "nightVision", "hunger" to "hunger", "weakness" to "weakness",
    "poison" to "poison", "wither" to "wither", "health_boost" to "healthBoost", "absorption" to "absorption",
    "saturation" to "saturation", "glowing" to "glowing", "levitation" to "levitation", "luck" to "luck",
    "unluck" to "unluck",
)

// Pre-1.13 entity names that differ from a plain PascalCase of the modern path, keyed by modern path.
private val ENTITY_LEGACY = mapOf(
    "magma_cube" to "LavaSlime", "ocelot" to "Ozelot", "wither" to "WitherBoss",
    "snow_golem" to "SnowMan", "snowman" to "SnowMan", "iron_golem" to "VillagerGolem",
    "mooshroom" to "MushroomCow", "mushroom_cow" to "MushroomCow",
    "zombified_piglin" to "PigZombie", "zombie_pigman" to "PigZombie", "pig_zombie" to "PigZombie",
    "tnt" to "PrimedTnt", "primed_tnt" to "PrimedTnt", "falling_block" to "FallingSand",
    "firework_rocket" to "FireworksRocketEntity", "firework" to "FireworksRocketEntity",
    "armor_stand" to "ArmorStand", "item_frame" to "ItemFrame", "experience_orb" to "XPOrb",
    "ender_pearl" to "ThrownEnderpearl", "egg" to "ThrownEgg", "leash_knot" to "LeashKnot", "leash_hitch" to "LeashKnot",
)
