package org.pnfolder.pnlibrary.localization

/**
 * Vanilla Minecraft translation-key categories, shared by every platform.
 *
 * Namespaced categories follow `<category>.<namespace>.<path>` (e.g. `item.minecraft.diamond`);
 * a few legacy categories such as game modes follow `<category>.<path>` (e.g. `gameMode.survival`).
 */
enum class TranslationCategory(val id: String, private val namespaced: Boolean = true) {
    BLOCK("block"),
    ITEM("item"),
    ENTITY("entity"),
    ENCHANTMENT("enchantment"),
    EFFECT("effect"),
    BIOME("biome"),
    COLOR("color"),
    GAME_MODE("gameMode", namespaced = false),
    ;

    /** Key prefix such as `item.minecraft.` (or `gameMode.`), used to match keys of this category. */
    fun prefix(namespace: String = VANILLA_NAMESPACE): String = if (namespaced) "$id.$namespace." else "$id."

    /** Full key such as `item.minecraft.diamond` (or `gameMode.survival`). */
    fun key(path: String, namespace: String = VANILLA_NAMESPACE): String =
        if (namespaced) "$id.$namespace.$path" else "$id.$path"

    companion object {
        const val VANILLA_NAMESPACE = "minecraft"
    }
}
