package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;

/**
 * Vanilla Minecraft translation-key categories, shared by every platform.
 *
 * <p>Namespaced categories follow {@code <category>.<namespace>.<path>} (e.g. {@code item.minecraft.diamond});
 * a few legacy categories such as game modes follow {@code <category>.<path>} (e.g. {@code gameMode.survival}).
 */
public enum TranslationCategory {
    BLOCK("block"),
    ITEM("item"),
    ENTITY("entity"),
    ENCHANTMENT("enchantment"),
    EFFECT("effect"),
    BIOME("biome"),
    COLOR("color"),
    GAME_MODE("gameMode", false);

    /** Namespace of vanilla Minecraft keys. */
    public static final String VANILLA_NAMESPACE = "minecraft";

    private final String id;
    private final boolean namespaced;

    TranslationCategory(String id) {
        this(id, true);
    }

    TranslationCategory(String id, boolean namespaced) {
        this.id = id;
        this.namespaced = namespaced;
    }

    /** Key prefix of the category, such as {@code item}. */
    @NotNull
    public String getId() {
        return id;
    }

    /** Key prefix such as {@code item.minecraft.} (or {@code gameMode.}) for the vanilla namespace. */
    @NotNull
    public String prefix() {
        return prefix(VANILLA_NAMESPACE);
    }

    /** Key prefix such as {@code item.minecraft.} (or {@code gameMode.}), used to match keys of this category. */
    @NotNull
    public String prefix(@NotNull String namespace) {
        return namespaced ? id + "." + namespace + "." : id + ".";
    }

    /** Full key such as {@code item.minecraft.diamond} (or {@code gameMode.survival}) in the vanilla namespace. */
    @NotNull
    public String key(@NotNull String path) {
        return key(path, VANILLA_NAMESPACE);
    }

    /** Full key such as {@code item.minecraft.diamond} (or {@code gameMode.survival}). */
    @NotNull
    public String key(@NotNull String path, @NotNull String namespace) {
        return namespaced ? id + "." + namespace + "." + path : id + "." + path;
    }
}
