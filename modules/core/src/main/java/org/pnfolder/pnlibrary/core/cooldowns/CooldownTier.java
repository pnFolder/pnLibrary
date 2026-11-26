package org.pnfolder.pnlibrary.core.cooldowns;

import java.time.Duration;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The wait for those who hold any of the permissions: a fixed length, or the base length times a factor. */
public final class CooldownTier {
    private final List<String> permissions;
    private final Duration length;
    private final Double factor;

    /**
     * @param permissions at least one permission; holding any of them selects the tier
     * @param length fixed wait, or {@code null} when {@code factor} is given
     * @param factor multiplier of the base wait, or {@code null} when {@code length} is given
     */
    public CooldownTier(@NotNull List<String> permissions, @Nullable Duration length, @Nullable Double factor) {
        if (permissions.isEmpty()) {
            throw new IllegalArgumentException("A tier needs at least one permission");
        }
        if (length == null && factor == null) {
            throw new IllegalArgumentException("A tier needs a length or a factor");
        }
        if (length != null && length.isNegative()) {
            throw new IllegalArgumentException("Cooldown duration cannot be negative");
        }
        if (factor != null && (factor < 0.0 || Double.isNaN(factor) || Double.isInfinite(factor))) {
            throw new IllegalArgumentException("A tier factor cannot be negative");
        }
        this.permissions = permissions;
        this.length = length;
        this.factor = factor;
    }

    public @NotNull List<String> getPermissions() {
        return permissions;
    }

    public @NotNull Duration lengthFor(@NotNull Duration base) {
        return length != null ? length : scaled(base, factor);
    }

    private static Duration scaled(Duration base, double by) {
        double nanos = base.toNanos() * by;
        return nanos >= Long.MAX_VALUE ? Duration.ofNanos(Long.MAX_VALUE) : Duration.ofNanos((long) nanos);
    }
}
