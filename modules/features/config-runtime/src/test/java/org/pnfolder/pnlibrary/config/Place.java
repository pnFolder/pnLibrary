package org.pnfolder.pnlibrary.config;

import java.util.Objects;

public final class Place {
    public final String world;
    public final int x;

    public Place() {
        this("none", 0);
    }

    public Place(String world, int x) {
        this.world = world;
        this.x = x;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Place && ((Place) other).world.equals(world) && ((Place) other).x == x;
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x);
    }

    @Override
    public String toString() {
        return "Place(world=" + world + ", x=" + x + ")";
    }
}
