package org.pnfolder.pnlibrary.bukkit.item;

import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates, reads and writes Bukkit items. Obtain it from {@code BukkitPlatform.getItems()}.
 *
 * <p>Three ways to get an item:
 * <ul>
 *   <li>by hand: {@code items.create(ItemSpec.builder().material("APPLE").name("&aApple").build())};</li>
 *   <li>from configuration in the default format: {@code items.fromSection(section)};</li>
 *   <li>from configuration in your own format: {@code items.fromSection(section, format)}, see {@link ItemFormat}.</li>
 * </ul>
 *
 * <p>To change a read item before creating it, read the spec first: {@code items.read(section).toBuilder()...}.
 *
 * <p>The default format:
 * <pre>{@code
 * material: DIAMOND_SWORD
 * amount: 1
 * name: '&bSword'
 * lore: ['&7Line']
 * enchantments: { sharpness: 5 }
 * texture: <hash, URL or Base64>   # makes a player head
 * item_data: <Base64>              # exact item, written by writeExact
 * }</pre>
 */
public interface ItemService {

    /** Builds an item stack from {@code spec}. */
    @NotNull ItemStack create(@NotNull ItemSpec spec);

    /** Reads {@code section} in the default format; {@code null} when the section is absent. */
    @Nullable ItemSpec read(@Nullable ConfigurationSection section);

    /** Reads {@code section} in {@code format}; {@code null} when the section is absent. */
    @Nullable ItemSpec read(@Nullable ConfigurationSection section, @NotNull ItemFormat format);

    /** Reads {@code values} in the default format; {@code null} when they are absent. */
    @Nullable ItemSpec read(@Nullable Map<?, ?> values);

    /** Reads {@code values} in {@code format}; {@code null} when they are absent. */
    @Nullable ItemSpec read(@Nullable Map<?, ?> values, @NotNull ItemFormat format);

    /** Reads and creates an item from {@code section} in the default format; {@code null} when the section is absent. */
    @Nullable ItemStack fromSection(@Nullable ConfigurationSection section);

    /** Reads and creates an item from {@code section} in {@code format}; {@code null} when the section is absent. */
    @Nullable ItemStack fromSection(@Nullable ConfigurationSection section, @NotNull ItemFormat format);

    /** Reads and creates an item from {@code values} in the default format; {@code null} when they are absent. */
    @Nullable ItemStack fromMap(@Nullable Map<?, ?> values);

    /** Reads and creates an item from {@code values} in {@code format}; {@code null} when they are absent. */
    @Nullable ItemStack fromMap(@Nullable Map<?, ?> values, @NotNull ItemFormat format);

    /** Replaces {@code key} in {@code parent} with a readable description of {@code item} in the default format. */
    void write(@NotNull ConfigurationSection parent, @NotNull String key, @NotNull ItemStack item);

    /** Replaces {@code key} in {@code parent} with a readable description of {@code item} in {@code format}. The item is not changed. */
    void write(@NotNull ConfigurationSection parent, @NotNull String key, @NotNull ItemStack item,
               @NotNull ItemFormat format);

    /** Like {@link #write}, plus a lossless payload that restores the item exactly. */
    void writeExact(@NotNull ConfigurationSection parent, @NotNull String key, @NotNull ItemStack item);

    /** Like {@link #write} in {@code format}, plus a lossless payload under {@link ItemFormat#getExact()}. */
    void writeExact(@NotNull ConfigurationSection parent, @NotNull String key, @NotNull ItemStack item,
                    @NotNull ItemFormat format);

    /** Returns material, amount and a lossless payload, or an empty map for an absent or air item. */
    @NotNull Map<String, Object> toMap(@Nullable ItemStack item);

    /**
     * Creates a player head from a Minecraft texture hash, a {@code textures.minecraft.net} URL or a Base64 payload
     * (an optional {@code base64:} prefix is accepted). An unrecognised texture gives a plain head.
     */
    @NotNull ItemStack head(@Nullable String texture, @Nullable String displayName);

    /** Encodes {@code item} completely as Base64; fails with {@link IllegalArgumentException} for an unsupported item. */
    @NotNull String encode(@NotNull ItemStack item);

    /**
     * Restores an item produced by {@link #encode}; fails with {@link IllegalArgumentException} for a malformed
     * payload. Decode only trusted data: the format uses Java object deserialization.
     */
    @NotNull ItemStack decode(@NotNull String encoded);

    /** Whether {@code item} is a real item: not {@code null} and not air. */
    boolean isReal(@Nullable ItemStack item);
}
