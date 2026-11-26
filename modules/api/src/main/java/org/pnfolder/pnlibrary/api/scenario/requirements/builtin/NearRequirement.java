package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;
import org.pnfolder.pnlibrary.api.world.Position;

import java.util.Objects;

/**
 * Met when the player is within {@code distance} blocks of {@code location}.
 *
 * @throws IllegalArgumentException when {@code location} is not {@code world,x,y,z} or {@code distance} is negative
 */
public final class NearRequirement implements Requirement {
    private final String location;
    private final double distance;

    /** Creates it with every setting at its default. */
    public NearRequirement() {
        this("world,0,0,0", 5.0);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public NearRequirement(@NotNull String location) {
        this(location, 5.0);
    }

    /** Creates it with every setting given. */
    public NearRequirement(@NotNull String location, double distance) {
        if (!(distance >= 0)) {
            throw new IllegalArgumentException("Distance must not be negative");
        }
        parse(location);
        this.location = location;
        this.distance = distance;
    }

    /** Point written as {@code world,x,y,z}. */
    @NotNull
    public String getLocation() {
        return location;
    }

    /** Largest allowed distance in blocks, not negative. */
    public double getDistance() {
        return distance;
    }

    private static Position parse(String location) {
        String[] parts = location.split(",", -1);
        if (parts.length != 4 || parts[0].trim().isEmpty()) {
            throw badLocation(location);
        }
        double[] numbers = new double[3];
        for (int i = 0; i < 3; i++) {
            try {
                numbers[i] = Double.parseDouble(parts[i + 1].trim());
            } catch (NumberFormatException error) {
                throw badLocation(location);
            }
        }
        return new Position(parts[0].trim(), numbers[0], numbers[1], numbers[2]);
    }

    private static IllegalArgumentException badLocation(String location) {
        return new IllegalArgumentException("Location must be written as world,x,y,z but was '" + location + "'");
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        Position here = context.getPlayer().position();
        Double current = here != null ? here.distanceTo(parse(location)) : null;
        if (current == null) {
            return new Verdict.Unmet("distance is not available here");
        }
        return Verdict.of(current <= distance, "too far from " + location);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NearRequirement)) {
            return false;
        }
        NearRequirement that = (NearRequirement) other;
        return Objects.equals(location, that.location)
                && Double.compare(distance, that.distance) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, distance);
    }

    @NotNull
    @Override
    public String toString() {
        return "NearRequirement(location=" + location + ", distance=" + distance + ")";
    }
}
