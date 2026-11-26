package org.pnfolder.pnlibrary.bukkit.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Description of an item, made by hand with {@link #builder()} or read from configuration with
 * {@link ItemService#read}. {@link ItemService#create} turns it into an item stack; the same spec can create any
 * number of items.
 *
 * <pre>{@code
 * ItemStack sword = items.create(ItemSpec.builder()
 *     .material("DIAMOND_SWORD")
 *     .name("&bHero sword")
 *     .enchant("sharpness", 5)
 *     .build());
 * }</pre>
 *
 * <p>Text accepts {@code &} colour codes and {@code &#RRGGBB}.
 */
public final class ItemSpec {
    private final String material;
    private final int amount;
    private final String name;
    private final List<String> lore;
    private final Map<String, Integer> enchantments;
    private final String texture;
    private final String exact;
    private final List<ItemCustomizer> customizers;

    private ItemSpec(Builder builder) {
        this.material = builder.material;
        this.amount = builder.amount;
        this.name = builder.name;
        this.lore = Collections.unmodifiableList(new ArrayList<>(builder.lore));
        this.enchantments = Collections.unmodifiableMap(new LinkedHashMap<>(builder.enchantments));
        this.texture = builder.texture;
        this.exact = builder.exact;
        this.customizers = Collections.unmodifiableList(new ArrayList<>(builder.customizers));
    }

    /** An empty builder: one stone. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Bukkit material name; an unknown one becomes stone. */
    @NotNull
    public String getMaterial() {
        return material;
    }

    /** Stack size, at least one. */
    public int getAmount() {
        return amount;
    }

    /** Display name, or {@code null} for the default. */
    @Nullable
    public String getName() {
        return name;
    }

    /** Lore lines. */
    @NotNull
    public List<String> getLore() {
        return lore;
    }

    /** Enchantment names ({@code sharpness} or {@code minecraft:sharpness}) and levels. */
    @NotNull
    public Map<String, Integer> getEnchantments() {
        return enchantments;
    }

    /** Player-head texture: hash, {@code textures.minecraft.net} URL or Base64; makes the item a head. */
    @Nullable
    public String getTexture() {
        return texture;
    }

    /** Lossless payload from {@link ItemService#encode}; when valid it replaces material and texture. */
    @Nullable
    public String getExact() {
        return exact;
    }

    /** Extra changes applied in order after everything above. */
    @NotNull
    public List<ItemCustomizer> getCustomizers() {
        return customizers;
    }

    /** Returns a builder that starts with every value of this spec. */
    @NotNull
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.material(material).amount(amount).name(name).lore(lore).texture(texture).exact(exact);
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            builder.enchant(entry.getKey(), entry.getValue());
        }
        for (ItemCustomizer customizer : customizers) {
            builder.customize(customizer);
        }
        return builder;
    }

    /** Fluent builder; every method returns the same builder. */
    public static final class Builder {
        private String material = "STONE";
        private int amount = 1;
        private String name;
        private final List<String> lore = new ArrayList<>();
        private final Map<String, Integer> enchantments = new LinkedHashMap<>();
        private String texture;
        private String exact;
        private final List<ItemCustomizer> customizers = new ArrayList<>();

        private Builder() {
        }

        /** Sets the Bukkit material name, for example {@code DIAMOND_SWORD}. */
        @NotNull
        public Builder material(@NotNull String value) {
            if (value.trim().isEmpty()) {
                throw new IllegalArgumentException("Item material must not be blank");
            }
            material = value.trim();
            return this;
        }

        /** Sets the stack size, at least one. */
        @NotNull
        public Builder amount(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("Item amount must be at least 1");
            }
            amount = value;
            return this;
        }

        /** Sets the display name; {@code null} keeps the default. */
        @NotNull
        public Builder name(@Nullable String value) {
            name = value;
            return this;
        }

        /** Replaces the lore with {@code lines}. */
        @NotNull
        public Builder lore(@NotNull List<String> lines) {
            List<String> copy = new ArrayList<>(lines);
            lore.clear();
            lore.addAll(copy);
            return this;
        }

        /** Replaces the lore with {@code lines}. */
        @NotNull
        public Builder lore(@NotNull String... lines) {
            return lore(Arrays.asList(lines));
        }

        /** Appends one line to the lore. */
        @NotNull
        public Builder addLore(@NotNull String line) {
            lore.add(line);
            return this;
        }

        /** Adds or replaces an enchantment by name, for example {@code sharpness}. */
        @NotNull
        public Builder enchant(@NotNull String enchantment, int level) {
            if (enchantment.trim().isEmpty()) {
                throw new IllegalArgumentException("Enchantment name must not be blank");
            }
            enchantments.put(enchantment.trim(), level);
            return this;
        }

        /** Makes the item a player head with {@code value} as its texture; {@code null} removes it. */
        @NotNull
        public Builder texture(@Nullable String value) {
            texture = trimToNull(value);
            return this;
        }

        /** Sets a payload from {@link ItemService#encode} that restores the item exactly; {@code null} removes it. */
        @NotNull
        public Builder exact(@Nullable String value) {
            exact = trimToNull(value);
            return this;
        }

        /** Adds a change for anything the spec has no field for; changes run in the order they were added. */
        @NotNull
        public Builder customize(@NotNull ItemCustomizer customizer) {
            customizers.add(customizer);
            return this;
        }

        /** Creates the immutable spec. */
        @NotNull
        public ItemSpec build() {
            return new ItemSpec(this);
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
