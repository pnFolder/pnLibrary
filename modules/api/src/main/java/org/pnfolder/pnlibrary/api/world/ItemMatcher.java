package org.pnfolder.pnlibrary.api.world;

import java.util.Objects;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes which items a player inventory check counts.
 *
 * <p>Every text field that is {@code null} is not compared. Color codes written with {@code &} are accepted in
 * the name and in the other text fields.
 */
public final class ItemMatcher {
    private final Key material;
    private final String name;
    private final String nameContains;
    private final String loreContains;
    private final boolean ignoreCase;

    /**
     * @param material item key such as {@code minecraft:diamond}
     * @param name full display name the item must have
     * @param nameContains text the display name must contain
     * @param loreContains text that at least one lore line must contain
     * @param ignoreCase whether text is compared without case sensitivity
     */
    public ItemMatcher(@NotNull Key material, @Nullable String name, @Nullable String nameContains,
                       @Nullable String loreContains, boolean ignoreCase) {
        this.material = Objects.requireNonNull(material, "material");
        this.name = name;
        this.nameContains = nameContains;
        this.loreContains = loreContains;
        this.ignoreCase = ignoreCase;
    }

    /** Counts every item of {@code material}, whatever its name or lore. */
    public ItemMatcher(@NotNull Key material) {
        this(material, null, null, null, true);
    }

    /** The item key such as {@code minecraft:diamond}. */
    public @NotNull Key getMaterial() {
        return material;
    }

    /** The full display name the item must have, or {@code null} when not compared. */
    public @Nullable String getName() {
        return name;
    }

    /** The text the display name must contain, or {@code null} when not compared. */
    public @Nullable String getNameContains() {
        return nameContains;
    }

    /** The text that at least one lore line must contain, or {@code null} when not compared. */
    public @Nullable String getLoreContains() {
        return loreContains;
    }

    /** Whether text is compared without case sensitivity. */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemMatcher)) {
            return false;
        }
        ItemMatcher that = (ItemMatcher) other;
        return ignoreCase == that.ignoreCase && material.equals(that.material) && Objects.equals(name, that.name)
            && Objects.equals(nameContains, that.nameContains) && Objects.equals(loreContains, that.loreContains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, name, nameContains, loreContains, ignoreCase);
    }

    @Override
    public String toString() {
        return "ItemMatcher(material=" + material + ", name=" + name + ", nameContains=" + nameContains
            + ", loreContains=" + loreContains + ", ignoreCase=" + ignoreCase + ")";
    }
}
