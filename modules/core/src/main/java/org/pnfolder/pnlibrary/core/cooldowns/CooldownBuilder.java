package org.pnfolder.pnlibrary.core.cooldowns;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.cooldowns.Cooldown;
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService;
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownDenied;
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownStarted;
import org.pnfolder.pnlibrary.api.cooldowns.builder.TierPick;

/** Collects what a plugin asks of one cooldown and builds the {@link NamedCooldown}. */
public final class CooldownBuilder implements Cooldown.Builder {
    private Duration duration;
    private final List<String> bypassPermissions = new ArrayList<String>();
    private final List<Predicate<Audience>> bypassConditions = new ArrayList<Predicate<Audience>>();
    private final List<CooldownTier> tiers = new ArrayList<CooldownTier>();
    private TierPick pick = TierPick.FIRST;
    private boolean shared;
    private Consumer<CooldownDenied> denied;
    private Consumer<CooldownStarted> started;

    @Override
    public @NotNull Cooldown.Builder duration(@NotNull Duration value) {
        duration = value;
        return this;
    }

    @Override
    public @NotNull Cooldown.Builder bypass(@NotNull String... permissions) {
        bypassPermissions.addAll(Arrays.asList(permissions));
        return this;
    }

    @Override
    public @NotNull Cooldown.Builder bypassIf(@NotNull Predicate<Audience> condition) {
        bypassConditions.add(condition);
        return this;
    }

    @Override
    public @NotNull Cooldown.Builder tier(@NotNull String permission, @NotNull Duration value) {
        return tier(Collections.singletonList(permission), value);
    }

    @Override
    public @NotNull Cooldown.Builder tier(@NotNull Collection<String> permissions, @NotNull Duration value) {
        tiers.add(new CooldownTier(new ArrayList<String>(permissions), value, null));
        return this;
    }

    @Override
    public @NotNull Cooldown.Builder tier(@NotNull String permission, double factor) {
        return tier(Collections.singletonList(permission), factor);
    }

    @Override
    public @NotNull Cooldown.Builder tier(@NotNull Collection<String> permissions, double factor) {
        tiers.add(new CooldownTier(new ArrayList<String>(permissions), null, factor));
        return this;
    }

    @Override
    public @NotNull Cooldown.Builder pick(@NotNull TierPick pick) {
        this.pick = pick;
        return this;
    }

    @Override
    public @NotNull Cooldown.Builder shared() {
        shared = true;
        return this;
    }

    @Override
    public @NotNull Cooldown.Builder onDenied(@NotNull Consumer<CooldownDenied> handler) {
        denied = handler;
        return this;
    }

    @Override
    public @NotNull Cooldown.Builder onStarted(@NotNull Consumer<CooldownStarted> handler) {
        started = handler;
        return this;
    }

    public @NotNull Cooldown build(@NotNull CooldownService service, @NotNull String action) {
        if (duration == null) {
            throw new IllegalArgumentException("Cooldown '" + action + "' needs a duration");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Cooldown duration cannot be negative");
        }
        CooldownSettings settings = new CooldownSettings(duration, new ArrayList<String>(bypassPermissions),
            new ArrayList<Predicate<Audience>>(bypassConditions), new ArrayList<CooldownTier>(tiers), pick, shared,
            denied, started);
        return new NamedCooldown(service, action, settings);
    }
}
