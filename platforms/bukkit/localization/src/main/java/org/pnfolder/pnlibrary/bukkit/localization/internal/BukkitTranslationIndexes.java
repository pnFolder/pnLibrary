package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import org.pnfolder.pnlibrary.localization.TranslationCategory;
import org.pnfolder.pnlibrary.localization.TranslationIndex;
import org.pnfolder.pnlibrary.localization.TranslationMatch;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

/** Reverse lookup: translated text to Bukkit value, one index per value type. */
public final class BukkitTranslationIndexes {
    private BukkitTranslationIndexes() {
    }

    /** Reverse index of item and block names. */
    public static TranslationIndex<Material> materials(Map<String, String> values, KeyboardLayouts layouts) {
        return reverseIndex(values, layouts,
            java.util.Arrays.asList(TranslationCategory.ITEM.prefix(), TranslationCategory.BLOCK.prefix()),
            Material::matchMaterial);
    }

    /** Reverse index of enchantment names. */
    public static TranslationIndex<Enchantment> enchantments(Map<String, String> values, KeyboardLayouts layouts) {
        return reverseIndex(values, layouts, prefixOf(TranslationCategory.ENCHANTMENT),
            BukkitTranslationIndexes::enchantmentOf);
    }

    /** Reverse index of entity names. */
    public static TranslationIndex<EntityType> entities(Map<String, String> values, KeyboardLayouts layouts) {
        return reverseIndex(values, layouts, prefixOf(TranslationCategory.ENTITY),
            BukkitTranslationIndexes::entityOf);
    }

    /** Reverse index of potion effect names. */
    public static TranslationIndex<PotionEffectType> effects(Map<String, String> values, KeyboardLayouts layouts) {
        return reverseIndex(values, layouts, prefixOf(TranslationCategory.EFFECT),
            BukkitTranslationIndexes::effectOf);
    }

    /** Reverse index of biome names. */
    public static TranslationIndex<Biome> biomes(Map<String, String> values, KeyboardLayouts layouts) {
        return reverseIndex(values, layouts, prefixOf(TranslationCategory.BIOME),
            BukkitTranslationIndexes::biomeOf);
    }

    /** Reverse index of dye color names. */
    public static TranslationIndex<DyeColor> colors(Map<String, String> values, KeyboardLayouts layouts) {
        return reverseIndex(values, layouts, prefixOf(TranslationCategory.COLOR), path -> {
            for (DyeColor color : DyeColor.values()) {
                if (color.name().equalsIgnoreCase(path)) {
                    return color;
                }
            }
            return null;
        });
    }

    /** Reverse index of game mode names. */
    public static TranslationIndex<GameMode> gameModes(Map<String, String> values, KeyboardLayouts layouts) {
        return reverseIndex(values, layouts, prefixOf(TranslationCategory.GAME_MODE), path -> {
            for (GameMode mode : GameMode.values()) {
                if (mode.name().equalsIgnoreCase(path)) {
                    return mode;
                }
            }
            return null;
        });
    }

    /** Builds an index of the entries whose key starts with one of {@code prefixes}; {@code resolve} maps the last key segment to a value. */
    private static <T> TranslationIndex<T> reverseIndex(Map<String, String> values, KeyboardLayouts layouts,
                                                        List<String> prefixes, Function<String, T> resolve) {
        List<TranslationMatch<T>> matches = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            if (!startsWithAny(key, prefixes)) {
                continue;
            }
            String last = key.substring(key.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
            matches.add(new TranslationMatch<>(key, entry.getValue(), resolve.apply(last)));
        }
        return TranslationIndex.of(matches, layouts);
    }

    private static boolean startsWithAny(String key, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> prefixOf(TranslationCategory category) {
        return Collections.singletonList(category.prefix());
    }

    /** Enchantment for a modern constant name, or null; tries the legacy alias and the namespaced key. */
    @SuppressWarnings("deprecation")
    private static Enchantment enchantmentOf(String modernName) {
        String alias = TranslationLegacyNames.ENCHANTMENT_ALIASES.get(modernName);
        String legacy = alias != null ? alias : modernName;
        Object constant = TranslationNames.staticField(Enchantment.class, legacy);
        if (constant instanceof Enchantment) {
            return (Enchantment) constant;
        }
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment.getName().equalsIgnoreCase(legacy) || keyMatches(enchantment, modernName)) {
                return enchantment;
            }
        }
        return null;
    }

    /** Entity type whose constant or namespaced key equals {@code path}, or null. */
    private static EntityType entityOf(String path) {
        for (EntityType type : EntityType.values()) {
            if (type.name().equalsIgnoreCase(path) || keyMatches(type, path)) {
                return type;
            }
        }
        return null;
    }

    /** Potion effect for a modern constant name, or null; tries the legacy alias and the namespaced key. */
    private static PotionEffectType effectOf(String modernName) {
        String alias = TranslationLegacyNames.EFFECT_ALIASES.get(modernName);
        String legacy = alias != null ? alias : modernName;
        Object constant = TranslationNames.staticField(PotionEffectType.class, legacy);
        if (constant instanceof PotionEffectType) {
            return (PotionEffectType) constant;
        }
        for (PotionEffectType effect : PotionEffectType.values()) {
            if (TranslationNames.effectName(effect).equalsIgnoreCase(legacy) || keyMatches(effect, modernName)) {
                return effect;
            }
        }
        return null;
    }

    /** Biome whose constant or namespaced key equals {@code path}, or null. */
    private static Biome biomeOf(String path) {
        for (Biome biome : Biome.values()) {
            if (biome.name().equalsIgnoreCase(path) || keyMatches(biome, path)) {
                return biome;
            }
        }
        return null;
    }

    private static boolean keyMatches(Object value, String path) {
        String key = TranslationNames.namespacedKey(value);
        return key != null && key.equalsIgnoreCase(path.toLowerCase(Locale.ROOT));
    }
}
