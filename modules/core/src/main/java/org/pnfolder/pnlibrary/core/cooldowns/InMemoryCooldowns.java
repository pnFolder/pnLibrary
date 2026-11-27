package org.pnfolder.pnlibrary.core.cooldowns;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.cooldowns.Cooldown;
import org.pnfolder.pnlibrary.api.cooldowns.CooldownResult;
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService;

/** In-memory, thread-safe cooldowns keyed by subject and action, measured on the monotonic clock. */
public final class InMemoryCooldowns implements CooldownService {
    private static final int SWEEP_EVERY = 256;

    private static final class Key {
        final UUID subject;
        final String action;

        Key(UUID subject, String action) {
            this.subject = subject;
            this.action = action;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) {
                return false;
            }
            Key key = (Key) other;
            return subject.equals(key.subject) && action.equals(key.action);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subject, action);
        }
    }

    private final ConcurrentHashMap<Key, Long> deadlines = new ConcurrentHashMap<Key, Long>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger writes = new AtomicInteger();

    @Override
    public int getSize() {
        cleanup();
        return deadlines.size();
    }

    @Override
    public @NotNull CooldownResult acquire(@NotNull UUID subject, @NotNull String action, @NotNull Duration duration) {
        validate(action, duration);
        checkOpen();
        Key key = new Key(subject, normalized(action));
        long now = System.nanoTime();
        sweepSometimes();
        while (true) {
            Long current = deadlines.get(key);
            if (current != null && current > now) {
                return CooldownResult.denied(nanos(current - now));
            }
            long target = deadline(now, duration);
            if (current == null && deadlines.putIfAbsent(key, target) == null) {
                return CooldownResult.allowed();
            }
            if (current != null && deadlines.replace(key, current, target)) {
                return CooldownResult.allowed();
            }
        }
    }

    @Override
    public boolean has(@NotNull UUID subject, @NotNull String action) {
        return !remaining(subject, action).isZero();
    }

    @Override
    public @NotNull Duration remaining(@NotNull UUID subject, @NotNull String action) {
        checkOpen();
        Key key = new Key(subject, normalized(action));
        Long deadline = deadlines.get(key);
        if (deadline == null) {
            return Duration.ZERO;
        }
        long left = deadline - System.nanoTime();
        if (left <= 0) {
            deadlines.remove(key, deadline);
            return Duration.ZERO;
        }
        return nanos(left);
    }

    @Override
    public void set(@NotNull UUID subject, @NotNull String action, @NotNull Duration duration) {
        validate(action, duration);
        checkOpen();
        sweepSometimes();
        Key key = new Key(subject, normalized(action));
        if (duration.isZero()) {
            deadlines.remove(key);
        } else {
            deadlines.put(key, deadline(System.nanoTime(), duration));
        }
    }

    @Override
    public @NotNull Duration extend(@NotNull UUID subject, @NotNull String action, @NotNull Duration duration) {
        validate(action, duration);
        checkOpen();
        sweepSometimes();
        Key key = new Key(subject, normalized(action));
        long now = System.nanoTime();
        // The remapping function never returns null, so compute always stores and returns a deadline.
        Long target = deadlines.compute(key, (k, old) -> deadline(Math.max(now, old == null ? now : old), duration));
        return nanos(target - now);
    }

    @Override
    public boolean reset(@NotNull UUID subject, @NotNull String action) {
        return deadlines.remove(new Key(subject, normalized(action))) != null;
    }

    @Override
    public void clear() {
        deadlines.clear();
    }

    @Override
    public int clear(@NotNull UUID subject) {
        int[] removed = {0};
        deadlines.keySet().removeIf(key -> {
            boolean mine = key.subject.equals(subject);
            if (mine) {
                removed[0]++;
            }
            return mine;
        });
        return removed[0];
    }

    @Override
    public @NotNull Map<String, Duration> active(@NotNull UUID subject) {
        checkOpen();
        long now = System.nanoTime();
        ArrayList<Map.Entry<Key, Long>> live = new ArrayList<Map.Entry<Key, Long>>();
        for (Map.Entry<Key, Long> entry : deadlines.entrySet()) {
            if (entry.getKey().subject.equals(subject) && entry.getValue() > now) {
                live.add(new java.util.AbstractMap.SimpleEntry<Key, Long>(entry.getKey(), entry.getValue()));
            }
        }
        Collections.sort(live, new Comparator<Map.Entry<Key, Long>>() {
            @Override
            public int compare(Map.Entry<Key, Long> a, Map.Entry<Key, Long> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        Map<String, Duration> result = new LinkedHashMap<String, Duration>();
        for (Map.Entry<Key, Long> entry : live) {
            result.put(entry.getKey().action, nanos(entry.getValue() - now));
        }
        return result;
    }

    @Override
    public @NotNull Cooldown cooldown(@NotNull String action, @NotNull Duration duration, @Nullable String bypassPermission) {
        return cooldown(action, builder -> {
            builder.duration(duration);
            if (bypassPermission != null) {
                builder.bypass(bypassPermission);
            }
        });
    }

    @Override
    public @NotNull Cooldown cooldown(@NotNull String action, @NotNull Consumer<Cooldown.Builder> configure) {
        normalized(action);
        CooldownBuilder builder = new CooldownBuilder();
        configure.accept(builder);
        return builder.build(this, action);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            clear();
        }
    }

    /** Every few hundred writes, drops the entries nobody asked about again, so the map cannot grow without bound. */
    private void sweepSometimes() {
        if (writes.incrementAndGet() % SWEEP_EVERY == 0) {
            cleanup();
        }
    }

    private void cleanup() {
        long now = System.nanoTime();
        deadlines.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Cooldown service is closed");
        }
    }

    private static String normalized(String action) {
        String value = action.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Cooldown action cannot be blank");
        }
        return value;
    }

    private static void validate(String action, Duration duration) {
        normalized(action);
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Cooldown duration cannot be negative");
        }
    }

    private static long deadline(long base, Duration duration) {
        try {
            return Math.addExact(base, duration.toNanos());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static Duration nanos(long value) {
        return Duration.ofNanos(Math.max(value, 0));
    }
}
