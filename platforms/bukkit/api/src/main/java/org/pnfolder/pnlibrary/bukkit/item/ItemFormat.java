package org.pnfolder.pnlibrary.bukkit.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * Which configuration keys {@link ItemService#read} uses for each item field, plus fields of your own.
 *
 * <pre>{@code
 * ItemFormat format = ItemFormat.builder()
 *     .material("type")
 *     .name("display-name")
 *     .field("glow", (item, value) -> { if (Boolean.TRUE.equals(value)) item.enchant("unbreaking", 1); })
 *     .build();
 * ItemStack reward = items.fromSection(config.getConfigurationSection("reward"), format);
 * }</pre>
 *
 * <p>Keys not listed keep their default names. A {@code material} written as {@code base64:<texture>} is read as
 * a texture.
 */
public final class ItemFormat {
    /** {@code material}, {@code amount}, {@code name}, {@code lore}, {@code enchantments}, {@code base64}/{@code texture} and {@code item_data}. */
    public static final ItemFormat DEFAULT = new Builder().build();

    private final String material;
    private final String amount;
    private final String name;
    private final String lore;
    private final String enchantments;
    private final List<String> textures;
    private final String exact;
    private final Map<String, ItemField> fields;

    private ItemFormat(Builder builder) {
        this.material = builder.material;
        this.amount = builder.amount;
        this.name = builder.name;
        this.lore = builder.lore;
        this.enchantments = builder.enchantments;
        this.textures = Collections.unmodifiableList(new ArrayList<>(builder.textures));
        this.exact = builder.exact;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.fields));
    }

    /** Creates an empty builder with the default keys. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    public String getMaterial() {
        return material;
    }

    @NotNull
    public String getAmount() {
        return amount;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getLore() {
        return lore;
    }

    @NotNull
    public String getEnchantments() {
        return enchantments;
    }

    @NotNull
    public List<String> getTextures() {
        return textures;
    }

    @NotNull
    public String getExact() {
        return exact;
    }

    @NotNull
    public Map<String, ItemField> getFields() {
        return fields;
    }

    /** Returns a builder that starts with every key and field of this format. */
    @NotNull
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.material(material).amount(amount).name(name).lore(lore).enchantments(enchantments)
            .texture(textures.toArray(new String[0])).exact(exact);
        for (Map.Entry<String, ItemField> entry : fields.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        return builder;
    }

    /** Fluent builder; every method returns the same builder. */
    public static final class Builder {
        private String material = "material";
        private String amount = "amount";
        private String name = "name";
        private String lore = "lore";
        private String enchantments = "enchantments";
        private List<String> textures = Arrays.asList("base64", "texture");
        private String exact = "item_data";
        private final Map<String, ItemField> fields = new LinkedHashMap<>();

        private Builder() {
        }

        /** Key of the material. Default {@code material}. */
        @NotNull
        public Builder material(@NotNull String key) {
            material = key(key);
            return this;
        }

        /** Key of the stack size. Default {@code amount}. */
        @NotNull
        public Builder amount(@NotNull String key) {
            amount = key(key);
            return this;
        }

        /** Key of the display name. Default {@code name}. */
        @NotNull
        public Builder name(@NotNull String key) {
            name = key(key);
            return this;
        }

        /** Key of the lore: a list of lines or a single line. Default {@code lore}. */
        @NotNull
        public Builder lore(@NotNull String key) {
            lore = key(key);
            return this;
        }

        /** Key of the enchantments section, {@code name: level}. Default {@code enchantments}. */
        @NotNull
        public Builder enchantments(@NotNull String key) {
            enchantments = key(key);
            return this;
        }

        /** Keys of the head texture, the first present one wins. Default {@code base64}, then {@code texture}. */
        @NotNull
        public Builder texture(@NotNull String... keys) {
            if (keys.length == 0) {
                throw new IllegalArgumentException("At least one texture key is required");
            }
            List<String> checked = new ArrayList<>(keys.length);
            for (String key : keys) {
                checked.add(key(key));
            }
            textures = checked;
            return this;
        }

        /** Key of the lossless payload written by {@link ItemService#writeExact}. Default {@code item_data}. */
        @NotNull
        public Builder exact(@NotNull String key) {
            exact = key(key);
            return this;
        }

        /** Reads a field of your own from {@code key}; it runs after the standard fields, in the order added. */
        @NotNull
        public Builder field(@NotNull String key, @NotNull ItemField field) {
            fields.put(key(key), field);
            return this;
        }

        /** Creates the immutable format. */
        @NotNull
        public ItemFormat build() {
            return new ItemFormat(this);
        }

        private static String key(String value) {
            if (value.trim().isEmpty()) {
                throw new IllegalArgumentException("Configuration key must not be blank");
            }
            return value.trim();
        }
    }
}
