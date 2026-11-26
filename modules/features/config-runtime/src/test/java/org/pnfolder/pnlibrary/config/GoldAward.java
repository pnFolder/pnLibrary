package org.pnfolder.pnlibrary.config;

public final class GoldAward implements Award {
    public int grams;

    public GoldAward() {
        this(1);
    }

    public GoldAward(int grams) {
        this.grams = grams;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GoldAward && ((GoldAward) other).grams == grams;
    }

    @Override
    public int hashCode() {
        return grams;
    }

    @Override
    public String toString() {
        return "GoldAward(grams=" + grams + ")";
    }
}
