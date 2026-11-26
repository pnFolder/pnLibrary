package org.pnfolder.pnlibrary.core.menu;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One item of a menu page, described without any platform type.
 *
 * <p>Each platform turns it into its own stack: the head with the texture when it can make one, otherwise the material
 * (or the alternative on old servers), named by the name and described by the lore. The legacy data is the damage value
 * of coloured glass on servers before 1.13.
 */
public final class ItemSpec {
    private final String texture;
    private final String material;
    private final String alternative;
    private final String name;
    private final List<String> lore;
    private final short legacyData;

    public ItemSpec(@Nullable String texture, @NotNull String material, @Nullable String alternative,
                    @NotNull String name, @NotNull List<String> lore, short legacyData) {
        this.texture = texture;
        this.material = Objects.requireNonNull(material, "material");
        this.alternative = alternative;
        this.name = Objects.requireNonNull(name, "name");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.legacyData = legacyData;
    }

    public ItemSpec(@Nullable String texture, @NotNull String material, @Nullable String alternative,
                    @NotNull String name, @NotNull List<String> lore) {
        this(texture, material, alternative, name, lore, (short) 0);
    }

    public @Nullable String getTexture() {
        return texture;
    }

    public @NotNull String getMaterial() {
        return material;
    }

    public @Nullable String getAlternative() {
        return alternative;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull List<String> getLore() {
        return lore;
    }

    public short getLegacyData() {
        return legacyData;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemSpec)) {
            return false;
        }
        ItemSpec that = (ItemSpec) other;
        return legacyData == that.legacyData && Objects.equals(texture, that.texture) && material.equals(that.material)
            && Objects.equals(alternative, that.alternative) && name.equals(that.name) && lore.equals(that.lore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(texture, material, alternative, name, lore, legacyData);
    }

    @Override
    public String toString() {
        return "ItemSpec(texture=" + texture + ", material=" + material + ", alternative=" + alternative
            + ", name=" + name + ", lore=" + lore + ", legacyData=" + legacyData + ")";
    }
}
