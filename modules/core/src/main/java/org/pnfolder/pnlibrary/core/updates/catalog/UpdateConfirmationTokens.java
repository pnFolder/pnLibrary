package org.pnfolder.pnlibrary.core.updates.catalog;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Single-use tokens that authorize one risky update operation for one player. */
public final class UpdateConfirmationTokens {
    private final SecureRandom random;
    private final Supplier<Instant> clock;
    private final ConcurrentHashMap<String, Grant> grants = new ConcurrentHashMap<>();

    /** What a token allows: who, for which plan and revision, and until when. */
    public static final class Grant {
        private final UUID player;
        private final UUID plan;
        private final long revision;
        private final UpdateAction action;
        private final Instant expiresAt;

        public Grant(@NotNull UUID player, @NotNull UUID plan, long revision, @NotNull UpdateAction action, @NotNull Instant expiresAt) {
            this.player = player;
            this.plan = plan;
            this.revision = revision;
            this.action = action;
            this.expiresAt = expiresAt;
        }

        public @NotNull UUID getPlayer() {
            return player;
        }

        public @NotNull UUID getPlan() {
            return plan;
        }

        public long getRevision() {
            return revision;
        }

        public @NotNull UpdateAction getAction() {
            return action;
        }

        public @NotNull Instant getExpiresAt() {
            return expiresAt;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Grant)) {
                return false;
            }
            Grant that = (Grant) other;
            return player.equals(that.player) && plan.equals(that.plan) && revision == that.revision
                && action == that.action && expiresAt.equals(that.expiresAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(player, plan, revision, action, expiresAt);
        }
    }

    public UpdateConfirmationTokens() {
        this(new SecureRandom(), Instant::now);
    }

    public UpdateConfirmationTokens(@NotNull SecureRandom random, @NotNull Supplier<Instant> clock) {
        this.random = random;
        this.clock = clock;
    }

    public @NotNull String issue(@NotNull UUID player, @NotNull UUID plan, long revision, @NotNull UpdateAction action,
                                 @NotNull Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        grants.put(token, new Grant(player, plan, revision, action, clock.get().plus(lifetime)));
        return token;
    }

    public boolean consume(@NotNull String token, @NotNull UUID player, @NotNull UUID plan, long revision, @NotNull UpdateAction action) {
        Grant grant = grants.get(token);
        if (grant == null) {
            return false;
        }
        if (!grant.player.equals(player) || !grant.plan.equals(plan) || grant.revision != revision || grant.action != action) {
            return false;
        }
        if (!clock.get().isBefore(grant.expiresAt)) {
            grants.remove(token, grant);
            return false;
        }
        return grants.remove(token, grant);
    }

    public @Nullable Grant redeem(@NotNull String token, @NotNull UUID player) {
        Grant grant = grants.get(token);
        if (grant == null || !grant.player.equals(player)) {
            return null;
        }
        if (!clock.get().isBefore(grant.expiresAt)) {
            grants.remove(token, grant);
            return null;
        }
        return grants.remove(token, grant) ? grant : null;
    }

    public int cleanup() {
        Instant now = clock.get();
        for (java.util.Map.Entry<String, Grant> entry : grants.entrySet()) {
            if (!now.isBefore(entry.getValue().expiresAt)) {
                grants.remove(entry.getKey(), entry.getValue());
            }
        }
        return grants.size();
    }
}
