package org.pnfolder.pnlibrary.bukkit.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Bukkit item service. Reading builds an {@link ItemSpec}, so read and hand-made items are created by the same code. */
public final class BukkitItems implements ItemService {
    public static final BukkitItems INSTANCE = new BukkitItems();

    private BukkitItems() {
    }

    @NotNull
    @Override
    public ItemStack create(@NotNull ItemSpec spec) {
        ItemStack item = decodeOrNull(spec.getExact());
        if (item == null && spec.getTexture() != null) {
            String name = spec.getName() != null ? spec.getName() : "&fItem";
            item = PlayerHeads.create(withoutPrefix(spec.getTexture()), name);
            item.setAmount(spec.getAmount());
        }
        if (item == null) {
            Material material = Material.matchMaterial(spec.getMaterial());
            item = new ItemStack(material != null ? material : Material.STONE, spec.getAmount());
        }
        applyMeta(item, spec);
        for (Map.Entry<String, Integer> entry : spec.getEnchantments().entrySet()) {
            String normalized = entry.getKey().toUpperCase(Locale.ROOT);
            normalized = normalized.startsWith("MINECRAFT:") ? normalized.substring(10) : normalized;
            Enchantment enchantment = Enchantment.getByName(normalized);
            if (enchantment != null) {
                item.addUnsafeEnchantment(enchantment, entry.getValue());
            }
        }
        for (ItemCustomizer customizer : spec.getCustomizers()) {
            customizer.apply(item);
        }
        return item;
    }

    private static void applyMeta(ItemStack item, ItemSpec spec) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (spec.getName() != null && !spec.getName().trim().isEmpty()) {
            meta.setDisplayName(ItemText.color(spec.getName()));
        }
        if (!spec.getLore().isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : spec.getLore()) {
                lore.add(ItemText.color(line));
            }
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    @Nullable
    @Override
    public ItemSpec read(@Nullable ConfigurationSection section) {
        return read(section, ItemFormat.DEFAULT);
    }

    @Nullable
    @Override
    public ItemSpec read(@Nullable ConfigurationSection section, @NotNull ItemFormat format) {
        return read(section == null ? null : section.getValues(false), format);
    }

    @Nullable
    @Override
    public ItemSpec read(@Nullable Map<?, ?> values) {
        return read(values, ItemFormat.DEFAULT);
    }

    @Nullable
    @Override
    public ItemSpec read(@Nullable Map<?, ?> values, @NotNull ItemFormat format) {
        return ItemSpecReader.read(values, format);
    }

    @Nullable
    @Override
    public ItemStack fromSection(@Nullable ConfigurationSection section) {
        return created(read(section));
    }

    @Nullable
    @Override
    public ItemStack fromSection(@Nullable ConfigurationSection section, @NotNull ItemFormat format) {
        return created(read(section, format));
    }

    @Nullable
    @Override
    public ItemStack fromMap(@Nullable Map<?, ?> values) {
        return created(read(values));
    }

    @Nullable
    @Override
    public ItemStack fromMap(@Nullable Map<?, ?> values, @NotNull ItemFormat format) {
        return created(read(values, format));
    }

    private ItemStack created(ItemSpec spec) {
        return spec == null ? null : create(spec);
    }

    @Override
    public void write(@NotNull ConfigurationSection parent, @NotNull String key, @NotNull ItemStack item) {
        write(parent, key, item, ItemFormat.DEFAULT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void write(@NotNull ConfigurationSection parent, @NotNull String key, @NotNull ItemStack item,
                      @NotNull ItemFormat format) {
        requireWritable(key, item);
        ItemStack copy = item.clone();
        parent.set(key, null);
        ConfigurationSection section = parent.createSection(key);
        section.set(format.getMaterial(), copy.getType().name());
        if (copy.getAmount() > 1) {
            section.set(format.getAmount(), copy.getAmount());
        }
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return;
        }
        if (meta.hasDisplayName()) {
            section.set(format.getName(), meta.getDisplayName());
        }
        if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
            section.set(format.getLore(), meta.getLore());
        }
        writeEnchantments(section, format, meta);
    }

    @SuppressWarnings("deprecation")
    private static void writeEnchantments(ConfigurationSection section, ItemFormat format, ItemMeta meta) {
        if (meta.getEnchants().isEmpty()) {
            return;
        }
        ConfigurationSection enchantments = section.createSection(format.getEnchantments());
        Map<String, Integer> sorted = new java.util.TreeMap<>();
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            sorted.put(entry.getKey().getName().toLowerCase(Locale.ROOT), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            enchantments.set(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void writeExact(@NotNull ConfigurationSection parent, @NotNull String key, @NotNull ItemStack item) {
        writeExact(parent, key, item, ItemFormat.DEFAULT);
    }

    @Override
    public void writeExact(@NotNull ConfigurationSection parent, @NotNull String key, @NotNull ItemStack item,
                           @NotNull ItemFormat format) {
        requireWritable(key, item);
        ItemStack copy = item.clone();
        String encoded = ItemStackCodec.encode(copy);
        write(parent, key, copy, format);
        parent.getConfigurationSection(key).set(format.getExact(), encoded);
    }

    @NotNull
    @Override
    public Map<String, Object> toMap(@Nullable ItemStack item) {
        if (item == null || !isReal(item)) {
            return Collections.emptyMap();
        }
        ItemStack copy = item.clone();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("material", copy.getType().name());
        if (copy.getAmount() > 1) {
            map.put("amount", copy.getAmount());
        }
        map.put("item_data", ItemStackCodec.encode(copy));
        return map;
    }

    @NotNull
    @Override
    public ItemStack head(@Nullable String texture, @Nullable String displayName) {
        return PlayerHeads.create(texture, displayName);
    }

    @NotNull
    @Override
    public String encode(@NotNull ItemStack item) {
        return ItemStackCodec.encode(item);
    }

    @NotNull
    @Override
    public ItemStack decode(@NotNull String encoded) {
        return ItemStackCodec.decode(encoded);
    }

    @Override
    public boolean isReal(@Nullable ItemStack item) {
        return item != null && item.getAmount() > 0 && !item.getType().name().equals("AIR")
            && !item.getType().name().endsWith("_AIR");
    }

    private static void requireWritable(String key, ItemStack item) {
        if (key.trim().isEmpty()) {
            throw new IllegalArgumentException("Item key must not be blank");
        }
        if (!INSTANCE.isReal(item)) {
            throw new IllegalArgumentException("Source must be a real item");
        }
    }

    private static String withoutPrefix(String value) {
        return ItemSpecReader.hasBase64Prefix(value) ? value.substring(7).trim() : value;
    }

    private static ItemStack decodeOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            ItemStack decoded = ItemStackCodec.decode(value);
            return INSTANCE.isReal(decoded) ? decoded : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
