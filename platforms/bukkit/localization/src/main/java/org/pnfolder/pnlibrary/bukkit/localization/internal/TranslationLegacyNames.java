package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Names of enchantments, effects and entities before Bukkit renamed its constants and before 1.13 keys. */
final class TranslationLegacyNames {
    /** Modern enchantment names to the legacy Bukkit constants. */
    static final Map<String, String> ENCHANTMENT_ALIASES = pairs(
        "PROTECTION", "PROTECTION_ENVIRONMENTAL", "FIRE_PROTECTION", "PROTECTION_FIRE",
        "FEATHER_FALLING", "PROTECTION_FALL", "BLAST_PROTECTION", "PROTECTION_EXPLOSIONS",
        "PROJECTILE_PROTECTION", "PROTECTION_PROJECTILE", "RESPIRATION", "OXYGEN",
        "AQUA_AFFINITY", "WATER_WORKER", "SHARPNESS", "DAMAGE_ALL",
        "SMITE", "DAMAGE_UNDEAD", "BANE_OF_ARTHROPODS", "DAMAGE_ARTHROPODS",
        "LOOTING", "LOOT_BONUS_MOBS", "EFFICIENCY", "DIG_SPEED",
        "UNBREAKING", "DURABILITY", "FORTUNE", "LOOT_BONUS_BLOCKS",
        "POWER", "ARROW_DAMAGE", "PUNCH", "ARROW_KNOCKBACK",
        "FLAME", "ARROW_FIRE", "INFINITY", "ARROW_INFINITE");

    /** Inverse of {@link #ENCHANTMENT_ALIASES}: legacy constant to modern name. */
    static final Map<String, String> ENCHANTMENT_LEGACY_TO_MODERN = inverse(ENCHANTMENT_ALIASES);

    /** Modern effect names to the legacy Bukkit constants. */
    static final Map<String, String> EFFECT_ALIASES = pairs(
        "SLOWNESS", "SLOW", "HASTE", "FAST_DIGGING", "MINING_FATIGUE", "SLOW_DIGGING",
        "STRENGTH", "INCREASE_DAMAGE", "INSTANT_HEALTH", "HEAL", "INSTANT_DAMAGE", "HARM",
        "JUMP_BOOST", "JUMP", "NAUSEA", "CONFUSION", "RESISTANCE", "DAMAGE_RESISTANCE");

    /** Inverse of {@link #EFFECT_ALIASES}: legacy constant to modern name. */
    static final Map<String, String> EFFECT_LEGACY_TO_MODERN = inverse(EFFECT_ALIASES);

    /** Pre-1.13 enchantment key fragments, keyed by the modern vanilla path so they ignore Bukkit's constant renames. */
    static final Map<String, String> ENCHANTMENT_LEGACY = pairs(
        "sharpness", "damage.all", "smite", "damage.undead", "bane_of_arthropods", "damage.arthropods",
        "protection", "protect.all", "fire_protection", "protect.fire", "feather_falling", "protect.fall",
        "blast_protection", "protect.explosion", "projectile_protection", "protect.projectile",
        "respiration", "oxygen", "aqua_affinity", "waterWorker", "thorns", "thorns",
        "depth_strider", "waterWalker", "frost_walker", "frostWalker", "binding_curse", "binding_curse",
        "knockback", "knockback", "fire_aspect", "fire", "looting", "lootBonus",
        "sweeping", "sweeping", "sweeping_edge", "sweeping", "efficiency", "digging",
        "silk_touch", "untouching", "unbreaking", "durability", "fortune", "lootBonusDigger",
        "luck_of_the_sea", "lootBonusFishing", "lure", "fishingSpeed", "power", "arrowDamage",
        "punch", "arrowKnockback", "flame", "arrowFire", "infinity", "arrowInfinite",
        "mending", "mending", "vanishing_curse", "vanishing_curse");

    /** Pre-1.13 effect key fragments by modern path. */
    static final Map<String, String> EFFECT_LEGACY = pairs(
        "speed", "moveSpeed", "slowness", "moveSlowdown", "haste", "digSpeed", "mining_fatigue", "digSlowDown",
        "strength", "damageBoost", "instant_health", "heal", "instant_damage", "harm", "jump_boost", "jump",
        "nausea", "confusion", "regeneration", "regeneration", "resistance", "resistance",
        "fire_resistance", "fireResistance", "water_breathing", "waterBreathing", "invisibility", "invisibility",
        "blindness", "blindness", "night_vision", "nightVision", "hunger", "hunger", "weakness", "weakness",
        "poison", "poison", "wither", "wither", "health_boost", "healthBoost", "absorption", "absorption",
        "saturation", "saturation", "glowing", "glowing", "levitation", "levitation", "luck", "luck",
        "unluck", "unluck");

    /** Pre-1.13 entity names that differ from a plain PascalCase of the modern path, keyed by modern path. */
    static final Map<String, String> ENTITY_LEGACY = pairs(
        "magma_cube", "LavaSlime", "ocelot", "Ozelot", "wither", "WitherBoss",
        "snow_golem", "SnowMan", "snowman", "SnowMan", "iron_golem", "VillagerGolem",
        "mooshroom", "MushroomCow", "mushroom_cow", "MushroomCow",
        "zombified_piglin", "PigZombie", "zombie_pigman", "PigZombie", "pig_zombie", "PigZombie",
        "tnt", "PrimedTnt", "primed_tnt", "PrimedTnt", "falling_block", "FallingSand",
        "firework_rocket", "FireworksRocketEntity", "firework", "FireworksRocketEntity",
        "armor_stand", "ArmorStand", "item_frame", "ItemFrame", "experience_orb", "XPOrb",
        "ender_pearl", "ThrownEnderpearl", "egg", "ThrownEgg", "leash_knot", "LeashKnot", "leash_hitch", "LeashKnot");

    private TranslationLegacyNames() {
    }

    private static Map<String, String> pairs(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> inverse(Map<String, String> source) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            map.put(entry.getValue(), entry.getKey());
        }
        return Collections.unmodifiableMap(map);
    }
}
