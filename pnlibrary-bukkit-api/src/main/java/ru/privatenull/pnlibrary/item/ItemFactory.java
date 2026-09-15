package ru.privatenull.pnlibrary.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts reusable Bukkit item configuration to and from {@link ItemStack}. */
public final class ItemFactory {
    private ItemFactory() {
    }

    /**
     * Builds an item from a Bukkit configuration section.
     *
     * @param section section containing material, amount, name, lore, enchantments, texture, or item_data
     * @return configured item, or {@code null} when the section is absent
     */
    public static ItemStack fromSection(ConfigurationSection section) {
        if (section == null) return null;
        ItemStack exact = decode(section.getString("item_data"));
        if (exact != null) {
            applyMeta(exact, section.contains("name") ? section.getString("name") : null,
                    section.isList("lore") ? section.getStringList("lore") : null,
                    section.getConfigurationSection("enchantments"));
            return exact;
        }

        String texture = firstTexture(section.getString("base64"), section.getString("texture"),
                section.getString("material"));
        ItemStack item = texture == null
                ? new ItemStack(material(section.getString("material")), Math.max(1, section.getInt("amount", 1)))
                : HeadUtil.create(texture, section.getString("name", "&fItem"));
        applyMeta(item, section.getString("name"), section.isList("lore") ? section.getStringList("lore") : null,
                section.getConfigurationSection("enchantments"));
        return item;
    }

    /**
     * Builds an item from a generic map, as returned by YAML libraries.
     *
     * @param values item configuration map
     * @return configured item, or {@code null} when the map is absent
     */
    public static ItemStack fromMap(Map<?, ?> values) {
        if (values == null) return null;
        ItemStack exact = decode(string(values.get("item_data"), null));
        if (exact != null) return exact;

        String texture = firstTexture(string(values.get("base64"), null),
                string(values.get("texture"), null), string(values.get("material"), null));
        ItemStack item = texture == null
                ? new ItemStack(material(string(values.get("material"), null)),
                        Math.max(1, integer(values.get("amount"), 1)))
                : HeadUtil.create(texture, string(values.get("name"), "&fItem"));
        applySimpleMeta(item, string(values.get("name"), null), strings(values.get("lore")));

        Object rawEnchantments = values.get("enchantments");
        if (rawEnchantments instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawEnchantments).entrySet()) {
                enchant(item, String.valueOf(entry.getKey()), integer(entry.getValue(), 1));
            }
        }
        return item;
    }

    /** Writes a readable item representation below {@code key}. */
    public static void writeItem(ConfigurationSection parent, String key, ItemStack source) {
        requireWritable(parent, key, source);
        parent.set(key, null);
        ConfigurationSection section = parent.createSection(key);
        ItemStack item = source.clone();
        section.set("material", item.getType().name());
        if (item.getAmount() > 1) section.set("amount", item.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (meta.hasDisplayName()) section.set("name", meta.getDisplayName());
        if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) section.set("lore", meta.getLore());
        if (!meta.getEnchants().isEmpty()) {
            ConfigurationSection enchantments = section.createSection("enchantments");
            List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(meta.getEnchants().entrySet());
            Collections.sort(entries, Comparator.comparing(ItemFactory::enchantmentName));
            for (Map.Entry<Enchantment, Integer> entry : entries) {
                enchantments.set(enchantmentName(entry), entry.getValue());
            }
        }
    }

    /** Writes a readable representation and a lossless {@code item_data} payload. */
    public static void writeExactItem(ConfigurationSection parent, String key, ItemStack source) {
        requireWritable(parent, key, source);
        ItemStack item = source.clone();
        item.setAmount(1);
        writeItem(parent, key, item);
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section != null) section.set("item_data", ItemStackCodec.encode(item));
    }

    /** Returns a compact map containing a readable material and lossless item payload. */
    public static Map<String, Object> toMap(ItemStack source) {
        if (!isRealItem(source)) return Collections.emptyMap();
        ItemStack item = source.clone();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("material", item.getType().name());
        if (item.getAmount() > 1) values.put("amount", item.getAmount());
        values.put("item_data", ItemStackCodec.encode(item));
        return values;
    }

    /** Returns whether an item is non-null, non-air, and has a positive amount. */
    public static boolean isRealItem(ItemStack item) {
        return item != null && !isAir(item.getType()) && item.getAmount() > 0;
    }

    private static void requireWritable(ConfigurationSection parent, String key, ItemStack source) {
        if (parent == null) throw new IllegalArgumentException("Parent section must not be null");
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Item key must not be blank");
        if (!isRealItem(source)) throw new IllegalArgumentException("Source must be a real item");
    }

    private static void applyMeta(ItemStack item, String name, List<String> lore, ConfigurationSection enchantments) {
        applySimpleMeta(item, name, lore);
        if (enchantments != null) {
            for (String key : enchantments.getKeys(false)) enchant(item, key, enchantments.getInt(key, 1));
        }
    }

    private static void applySimpleMeta(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (name != null && !name.trim().isEmpty()) meta.setDisplayName(ItemText.color(name));
        if (lore != null && !lore.isEmpty()) {
            List<String> colored = new ArrayList<>(lore.size());
            for (String line : lore) colored.add(ItemText.color(line));
            meta.setLore(colored);
        }
        item.setItemMeta(meta);
    }

    private static void enchant(ItemStack item, String key, int level) {
        String normalized = key.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        Enchantment enchantment = Enchantment.getByName(normalized);
        if (enchantment != null) item.addUnsafeEnchantment(enchantment, level);
    }

    private static String enchantmentName(Map.Entry<Enchantment, Integer> entry) {
        return entry.getKey().getName().toLowerCase(Locale.ROOT);
    }

    private static Material material(String value) {
        Material material = Material.matchMaterial(value == null || value.trim().isEmpty() ? "STONE" : value);
        return material == null ? Material.STONE : material;
    }

    private static boolean isAir(Material material) {
        String name = material.name();
        return name.equals("AIR") || name.endsWith("_AIR");
    }

    private static String firstTexture(String base64, String texture, String material) {
        String value = normalize(base64);
        if (value == null) value = normalize(texture);
        if (value == null && material != null) {
            String lower = material.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("base64-") || lower.startsWith("base64:")) value = normalize(material);
        }
        return value;
    }

    private static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("base64-") || lower.startsWith("base64:")) normalized = normalized.substring(7).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static ItemStack decode(String value) {
        if (value == null || value.trim().isEmpty()) return null;
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
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?>)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (Object entry : (List<?>) value) if (entry instanceof String) result.add((String) entry);
        return result;
    }

}
