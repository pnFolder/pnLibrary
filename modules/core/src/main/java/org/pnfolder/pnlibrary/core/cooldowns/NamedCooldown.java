package org.pnfolder.pnlibrary.core.cooldowns;

import java.util.Objects;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.cooldowns.Cooldown;
import org.pnfolder.pnlibrary.api.cooldowns.CooldownResult;
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService;
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownDenied;
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownStarted;
import java.util.function.Predicate;

/** A {@link Cooldown} over a service, kept under one action name and behaving as its settings say. */
public final class NamedCooldown implements Cooldown {
    private static final UUID SHARED = new UUID(0, 0);

    private final CooldownService service;
    private final String action;
    private final CooldownSettings settings;

    public NamedCooldown(@NotNull CooldownService service, @NotNull String action, @NotNull CooldownSettings settings) {
        this.service = Objects.requireNonNull(service, "service");
        this.action = Objects.requireNonNull(action, "action");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public @NotNull String getAction() {
        return action;
    }

    @Override
    public @NotNull Duration getDuration() {
        return settings.getDuration();
    }

    @Override
    public @NotNull Duration durationFor(@NotNull Audience who) {
        List<Duration> held = new ArrayList<>();
        for (CooldownTier tier : settings.getTiers()) {
            if (holdsAny(who, tier.getPermissions())) {
                held.add(tier.lengthFor(settings.getDuration()));
            }
        }
        if (held.isEmpty()) {
            return settings.getDuration();
        }
        switch (settings.getPick()) {
            case SHORTEST:
                return Collections.min(held);
            case LONGEST:
                return Collections.max(held);
            default:
                return held.get(0);
        }
    }

    @Override
    public @NotNull CooldownResult acquire(@NotNull UUID subject) {
        return service.acquire(key(subject), action, settings.getDuration());
    }

    @Override
    public @NotNull CooldownResult acquire(@NotNull Audience who) {
        UUID subject = subjectOf(who);
        if (subject == null || bypasses(who)) {
            return CooldownResult.allowed();
        }
        Duration length = durationFor(who);
        CooldownResult result = service.acquire(key(subject), action, length);
        Consumer<CooldownStarted> started = settings.getOnStarted();
        if (result.getAllowed() && started != null) {
            started.accept(new CooldownStarted(who, action, length));
        }
        return result;
    }

    @Override
    public @NotNull CooldownResult run(@NotNull Audience who, @NotNull Runnable task) {
        CooldownResult result = acquire(who);
        if (result.getAllowed()) {
            task.run();
        } else {
            deny(who, result);
        }
        return result;
    }

    @Override
    public @NotNull Duration remaining(@NotNull Audience who) {
        UUID subject = subjectOf(who);
        if (subject == null || bypasses(who)) {
            return Duration.ZERO;
        }
        return service.remaining(key(subject), action);
    }

    @Override
    public boolean reset(@NotNull UUID subject) {
        return service.reset(key(subject), action);
    }

    @Override
    public boolean reset(@NotNull Audience who) {
        UUID subject = subjectOf(who);
        return subject != null && reset(subject);
    }

    private void deny(Audience who, CooldownResult result) {
        CooldownDenied denied = new CooldownDenied(who, action, result.getRemaining());
        Consumer<CooldownDenied> handler = settings.getOnDenied();
        if (handler != null) {
            handler.accept(denied);
        } else {
            who.sendMessage(Component.text("Подождите ещё " + denied.getRemainingSeconds() + " с.", ChatPalette.TEXT));
        }
    }

    private boolean bypasses(Audience who) {
        if (holdsAny(who, settings.getBypassPermissions())) {
            return true;
        }
        for (Predicate<Audience> condition : settings.getBypassConditions()) {
            if (condition.test(who)) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsAny(Audience who, List<String> permissions) {
        for (String permission : permissions) {
            if (AudienceExtensions.hasPermission(who, permission)) {
                return true;
            }
        }
        return false;
    }

    private UUID key(UUID subject) {
        return settings.isShared() ? SHARED : subject;
    }

    private static UUID subjectOf(Audience who) {
        return who.get(Identity.UUID).orElse(null);
    }
}
