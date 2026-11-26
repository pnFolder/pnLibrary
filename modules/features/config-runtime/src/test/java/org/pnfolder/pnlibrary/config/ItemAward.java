package org.pnfolder.pnlibrary.config;

import java.util.Objects;

public final class ItemAward implements Award {
    public String material;
    public int count;

    public ItemAward() {
        this("STONE", 1);
    }

    public ItemAward(String material, int count) {
        this.material = material;
        this.count = count;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemAward && ((ItemAward) other).material.equals(material) && ((ItemAward) other).count == count;
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, count);
    }

    @Override
    public String toString() {
        return "ItemAward(material=" + material + ", count=" + count + ")";
    }
}
