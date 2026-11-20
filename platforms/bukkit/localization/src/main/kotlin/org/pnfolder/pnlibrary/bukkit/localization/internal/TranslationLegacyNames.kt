package org.pnfolder.pnlibrary.bukkit.localization.internal

/** Names of enchantments, effects and entities before Bukkit renamed its constants and before 1.13 keys. */
internal val ENCHANTMENT_ALIASES = mapOf(
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

/** Inverse of [ENCHANTMENT_ALIASES]: legacy constant to modern name. */
internal val ENCHANTMENT_LEGACY_TO_MODERN: Map<String, String> =
    ENCHANTMENT_ALIASES.entries.associate { (modern, legacy) -> legacy to modern }

/** Modern effect names to the legacy Bukkit constants. */
internal val EFFECT_ALIASES = mapOf(
    "SLOWNESS" to "SLOW", "HASTE" to "FAST_DIGGING", "MINING_FATIGUE" to "SLOW_DIGGING",
    "STRENGTH" to "INCREASE_DAMAGE", "INSTANT_HEALTH" to "HEAL", "INSTANT_DAMAGE" to "HARM",
    "JUMP_BOOST" to "JUMP", "NAUSEA" to "CONFUSION", "RESISTANCE" to "DAMAGE_RESISTANCE",
)

/** Inverse of [EFFECT_ALIASES]: legacy constant to modern name. */
internal val EFFECT_LEGACY_TO_MODERN: Map<String, String> =
    EFFECT_ALIASES.entries.associate { (modern, legacy) -> legacy to modern }

// Pre-1.13 key fragments, keyed by the modern vanilla path so they are independent of Bukkit's constant renames.
internal val ENCHANTMENT_LEGACY = mapOf(
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

/** Pre-1.13 effect key fragments by modern path. */
internal val EFFECT_LEGACY = mapOf(
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
internal val ENTITY_LEGACY = mapOf(
    "magma_cube" to "LavaSlime", "ocelot" to "Ozelot", "wither" to "WitherBoss",
    "snow_golem" to "SnowMan", "snowman" to "SnowMan", "iron_golem" to "VillagerGolem",
    "mooshroom" to "MushroomCow", "mushroom_cow" to "MushroomCow",
    "zombified_piglin" to "PigZombie", "zombie_pigman" to "PigZombie", "pig_zombie" to "PigZombie",
    "tnt" to "PrimedTnt", "primed_tnt" to "PrimedTnt", "falling_block" to "FallingSand",
    "firework_rocket" to "FireworksRocketEntity", "firework" to "FireworksRocketEntity",
    "armor_stand" to "ArmorStand", "item_frame" to "ItemFrame", "experience_orb" to "XPOrb",
    "ender_pearl" to "ThrownEnderpearl", "egg" to "ThrownEgg", "leash_knot" to "LeashKnot", "leash_hitch" to "LeashKnot",
)
