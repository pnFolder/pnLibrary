package ru.privatenull.pnlibrary.localization;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Public Minecraft item localization service backed by pnLibrary's bundled language files.
 * Instances are immutable and safe to share for the lifetime of a plugin configuration.
 */
public final class ItemLocalization {
    private final MinecraftLocale locale;
    private final Map<String, String> translations;

    private ItemLocalization(MinecraftLocale locale, Map<String, String> translations) {
        this.locale = locale;
        this.translations = Collections.unmodifiableMap(new LinkedHashMap<>(translations));
    }

    public static ItemLocalization load(String locale) {
        return load(MinecraftLocale.parse(locale));
    }

    public static ItemLocalization load(MinecraftLocale locale) {
        MinecraftLocale selected = locale == null ? MinecraftLocale.RU_RU : locale;
        String resource = "/pnlibrary/lang/" + selected.id() + ".json";
        try (InputStream input = ItemLocalization.class.getResourceAsStream(resource)) {
            return new ItemLocalization(selected, FlatJsonTranslations.read(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load Minecraft locale " + selected.id(), exception);
        }
    }

    public MinecraftLocale locale() {
        return locale;
    }

    public String translate(String key) {
        return key == null ? null : translations.get(key);
    }

    public Map<String, String> translations() {
        return translations;
    }

    /** Returns every material available on the running server and its localized display name. */
    public Map<Material, String> materialNames() {
        Map<Material, String> result = new LinkedHashMap<>();
        for (Material material : Material.values()) {
            if (!material.isAir()) result.put(material, getMaterialName(material));
        }
        return Collections.unmodifiableMap(result);
    }

    public Component getNameComponent(ItemStack stack) {
        return Component.text(getPlainName(stack));
    }

    public String getPlainName(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return locale.itemFallback();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            String name = ChatColor.stripColor(displayName == null ? "" : displayName).trim();
            if (!name.isEmpty()) return name;
        }
        if (meta instanceof PotionMeta potionMeta) {
            return getPotionName(stack.getType(), potionMeta.getBasePotionType());
        }
        return getMaterialName(stack.getType());
    }

    public String getItemKey(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return Material.AIR.name();
        if (stack.getItemMeta() instanceof PotionMeta potionMeta) {
            PotionVariant variant = PotionVariant.from(potionMeta.getBasePotionType());
            return stack.getType().name() + ":" + variant.baseType() + ":"
                    + variant.extended() + ":" + variant.upgraded();
        }
        return stack.getType().name();
    }

    public Material getKeyMaterial(String key) {
        if (key == null || key.isBlank()) return null;
        return Material.matchMaterial(key.split(":", 2)[0]);
    }

    public ItemStack createItem(String key) {
        Material material = getKeyMaterial(key);
        if (material == null || material.isAir()) return new ItemStack(Material.PAPER);
        ItemStack item = new ItemStack(material);
        String[] parts = key.split(":");
        if (parts.length == 4 && item.getItemMeta() instanceof PotionMeta meta) {
            try {
                PotionVariant variant = new PotionVariant(
                        normalizeLegacyPotionType(parts[1]),
                        Boolean.parseBoolean(parts[2]),
                        Boolean.parseBoolean(parts[3])
                );
                meta.setBasePotionType(variant.toPotionType());
                item.setItemMeta(meta);
            } catch (IllegalArgumentException ignored) {
                // The base material remains a safe fallback for outdated saved potion variants.
            }
        }
        return item;
    }

    public String getItemName(String key) {
        return getPlainName(createItem(key));
    }

    public String getMaterialName(Material material) {
        if (material == null || material.isAir()) return locale.itemFallback();
        String path = material.name().toLowerCase(Locale.ROOT);
        String localized = translate("item.minecraft." + path);
        if ((localized == null || localized.isBlank()) && material.isBlock()) {
            localized = translate("block.minecraft." + path);
        }
        if (localized != null && !localized.isBlank()) return localized;
        return readableFallback(material.name());
    }

    public String getEnchantmentName(Enchantment enchantment) {
        if (enchantment == null) return locale.enchantmentFallback();
        String localized = translate("enchantment.minecraft." + enchantment.getKey().getKey());
        return localized == null || localized.isBlank() ? locale.unknownEnchantment() : localized;
    }

    public Material matchMaterial(String value) {
        if (value == null || value.isBlank()) return null;
        Material direct = Material.matchMaterial(value.trim());
        if (direct != null && !direct.isAir()) return direct;
        String normalized = normalize(value);
        for (Material material : Material.values()) {
            if (!material.isAir() && normalize(getMaterialName(material)).equals(normalized)) return material;
        }
        return null;
    }

    private String getPotionName(Material material, PotionType potionType) {
        String prefix = switch (material.name()) {
            case "SPLASH_POTION" -> "splash_potion";
            case "LINGERING_POTION" -> "lingering_potion";
            case "TIPPED_ARROW" -> "tipped_arrow";
            default -> "potion";
        };
        PotionVariant variant = PotionVariant.from(potionType);
        String effect = switch (variant.baseType()) {
            case "JUMP" -> "leaping";
            case "SPEED" -> "swiftness";
            case "INSTANT_HEAL" -> "healing";
            case "INSTANT_DAMAGE" -> "harming";
            case "REGEN" -> "regeneration";
            default -> variant.baseType().toLowerCase(Locale.ROOT);
        };
        String localized = translate("item.minecraft." + prefix + ".effect." + effect);
        if (localized == null || localized.isBlank()) localized = getMaterialName(material);
        if (variant.upgraded()) return localized + " (" + locale.upgraded() + ")";
        if (variant.extended()) return localized + " (" + locale.extended() + ")";
        return localized;
    }

    private static String normalizeLegacyPotionType(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "SPEED" -> "SWIFTNESS";
            case "JUMP" -> "LEAPING";
            case "INSTANT_HEAL" -> "HEALING";
            case "INSTANT_DAMAGE" -> "HARMING";
            case "REGEN" -> "REGENERATION";
            default -> type.toUpperCase(Locale.ROOT);
        };
    }

    private String readableFallback(String value) {
        String raw = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (locale == MinecraftLocale.RU_RU) return "Неизвестный предмет («" + raw + "»)";
        if (raw.isEmpty()) return locale.itemFallback();
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private String normalize(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s{2,}", " ");
        return locale == MinecraftLocale.RU_RU ? normalized.replace('ё', 'е') : normalized;
    }

    private record PotionVariant(String baseType, boolean extended, boolean upgraded) {

        private PotionVariant {
            if (extended && upgraded) {
                throw new IllegalArgumentException("A potion cannot be both extended and upgraded");
            }
        }

        static PotionVariant from(PotionType type) {
            String name = type == null ? PotionType.WATER.name() : type.name();
            if (name.startsWith("LONG_")) {
                return new PotionVariant(name.substring("LONG_".length()), true, false);
            }
            if (name.startsWith("STRONG_")) {
                return new PotionVariant(name.substring("STRONG_".length()), false, true);
            }
            return new PotionVariant(name, false, false);
        }

        PotionType toPotionType() {
            String prefix = extended ? "LONG_" : upgraded ? "STRONG_" : "";
            return PotionType.valueOf(prefix + baseType);
        }
    }
}
