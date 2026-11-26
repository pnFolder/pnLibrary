package org.pnfolder.pnlibrary.core.cooldowns;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownDenied;
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownStarted;
import org.pnfolder.pnlibrary.api.cooldowns.builder.TierPick;

/** Everything a {@link NamedCooldown} was configured with. */
public final class CooldownSettings {
    private final Duration duration;
    private final List<String> bypassPermissions;
    private final List<Predicate<Audience>> bypassConditions;
    private final List<CooldownTier> tiers;
    private final TierPick pick;
    private final boolean shared;
    private final Consumer<CooldownDenied> onDenied;
    private final Consumer<CooldownStarted> onStarted;

    public CooldownSettings(@NotNull Duration duration, @NotNull List<String> bypassPermissions,
                            @NotNull List<Predicate<Audience>> bypassConditions, @NotNull List<CooldownTier> tiers,
                            @NotNull TierPick pick, boolean shared, @Nullable Consumer<CooldownDenied> onDenied,
                            @Nullable Consumer<CooldownStarted> onStarted) {
        this.duration = duration;
        this.bypassPermissions = bypassPermissions;
        this.bypassConditions = bypassConditions;
        this.tiers = tiers;
        this.pick = pick;
        this.shared = shared;
        this.onDenied = onDenied;
        this.onStarted = onStarted;
    }

    public @NotNull Duration getDuration() {
        return duration;
    }

    public @NotNull List<String> getBypassPermissions() {
        return bypassPermissions;
    }

    public @NotNull List<Predicate<Audience>> getBypassConditions() {
        return bypassConditions;
    }

    public @NotNull List<CooldownTier> getTiers() {
        return tiers;
    }

    public @NotNull TierPick getPick() {
        return pick;
    }

    public boolean isShared() {
        return shared;
    }

    public @Nullable Consumer<CooldownDenied> getOnDenied() {
        return onDenied;
    }

    public @Nullable Consumer<CooldownStarted> getOnStarted() {
        return onStarted;
    }
}
