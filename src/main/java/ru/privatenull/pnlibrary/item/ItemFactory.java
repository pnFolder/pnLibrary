package ru.privatenull.pnlibrary.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts reusable Bukkit item configuration to and from ItemStack. */
public final class ItemFactory {
    private ItemFactory() {
    }

    public static ItemStack fromSection(ConfigurationSection section) {
        if (section == null) return null;
        ItemStack exact = deserialize(section.getString("item_data"));
        if (exact != null) {
            applyMeta(exact,
                    section.contains("name", true) ? section.getString("name") : null,
                    section.isList("lore") ? section.getStringList("lore") : null,
                    section.getConfigurationSection("enchantments"));
            return exact;
        }

        String texture = texture(section.getString("base64"), section.getString("material"));
        ItemStack item = texture == null
                ? new ItemStack(material(section.getString("material")), Math.max(1, section.getInt("amount", 1)))
                : HeadUtil.create(texture, ColorUtil.colorize(section.getString("name", "&fItem")));
        applyMeta(item, section.getString("name"), section.getStringList("lore"),
                section.getConfigurationSection("enchantments"));
        return item;
    }

    public static ItemStack fromMap(Map<?, ?> values) {
        if (values == null) return null;
        ItemStack exact = deserialize(string(values.get("item_data"), null));
        if (exact != null) return exact;

        String texture = normalize(string(values.get("base64"), null));
        if (texture == null) texture = normalize(string(values.get("texture"), null));
        if (texture == null) texture = materialTexture(string(values.get("material"), null));
        ItemStack item = texture == null
                ? new ItemStack(material(string(values.get("material"), null)),
                        Math.max(1, integer(values.get("amount"), 1)))
                : HeadUtil.create(texture, ColorUtil.colorize(string(values.get("name"), "&fItem")));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = string(values.get("name"), null);
            if (name != null) meta.setDisplayName(ColorUtil.colorize(name));
            List<String> lore = strings(values.get("lore"));
            if (!lore.isEmpty()) meta.setLore(lore.stream().map(ColorUtil::colorize).toList());
            item.setItemMeta(meta);
        }
        Object enchantments = values.get("enchantments");
        if (enchantments instanceof Map<?, ?> map) {
            map.forEach((key, level) -> enchant(item, String.valueOf(key), integer(level, 1)));
        }
        return item;
    }

    public static void writeItem(ConfigurationSection parent, String key, ItemStack source) {
        parent.set(key, null);
        ConfigurationSection section = parent.createSection(key);
        ItemStack item = source.clone();
        item.setAmount(Math.max(1, item.getAmount()));
        section.set("material", item.getType().name());
        if (item.getAmount() > 1) section.set("amount", item.getAmount());

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (meta.hasDisplayName()) section.set("name", meta.getDisplayName());
        if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
            section.set("lore", meta.getLore());
        }
        if (!meta.getEnchants().isEmpty()) {
            ConfigurationSection enchantments = section.createSection("enchantments");
            meta.getEnchants().entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().getKey().getKey()))
                    .forEach(entry -> enchantments.set(entry.getKey().getKey().getKey(), entry.getValue()));
        }
    }

    public static void writeExactItem(ConfigurationSection parent, String key, ItemStack source) {
        ItemStack item = source.clone();
        item.setAmount(1);
        writeItem(parent, key, item);
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) return;
        try {
            section.set("item_data", ItemStackCodec.encode(item));
        } catch (IllegalArgumentException exception) {
            section.set("item_data", null);
        }
    }

    /** Creates the canonical compact YAML map while retaining the exact Bukkit item payload. */
    public static Map<String, Object> toMap(ItemStack source) {
        if (!isRealItem(source)) return Map.of();
        ItemStack item = source.clone();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("material", item.getType().name());
        if (item.getAmount() > 1) values.put("amount", item.getAmount());
        String encoded = ItemStackCodec.encode(item);
        if (encoded != null) values.put("item_data", encoded);
        return values;
    }

    public static boolean isRealItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    private static void applyMeta(ItemStack item, String name, List<String> lore,
                                  ConfigurationSection enchantments) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isBlank()) meta.setDisplayName(ColorUtil.colorize(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(ColorUtil::colorize).toList());
            }
            item.setItemMeta(meta);
        }
        if (enchantments != null) {
            for (String key : enchantments.getKeys(false)) {
                enchant(item, key, enchantments.getInt(key, 1));
            }
        }
    }

    private static void enchant(ItemStack item, String key, int level) {
        Enchantment enchantment = Enchantment.getByKey(
                NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
        if (enchantment != null) item.addUnsafeEnchantment(enchantment, level);
    }

    private static Material material(String value) {
        Material material = Material.matchMaterial(value == null || value.isBlank() ? "STONE" : value);
        return material == null ? Material.STONE : material;
    }

    private static String texture(String base64, String material) {
        String texture = normalize(base64);
        return texture == null ? materialTexture(material) : texture;
    }

    private static String materialTexture(String material) {
        if (material == null) return null;
        String lower = material.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("base64-") || lower.startsWith("base64:") ? normalize(material) : null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("base64-") || lower.startsWith("base64:")) {
            normalized = normalized.substring(7).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static ItemStack deserialize(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            ItemStack item = ItemStackCodec.decode(value);
            return isRealItem(item) ? item : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object entry : list) if (entry instanceof String text) result.add(text);
        return List.copyOf(result);
    }
}
