package org.pnfolder.pnlibrary.core.events;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

// Invalidating T drops only entries for classes T matches, so churn on one event never slows others.
// Lists built during a change are returned but not cached. Weak keys allow plugin class GC.
public final class HandlerCache<S> {
    private final Supplier<? extends Iterable<S>> all;
    private final BiPredicate<S, Class<?>> matches;
    private final Comparator<S> order;
    private final AtomicLong generation = new AtomicLong();
    private final WeakHashMap<Class<?>, List<S>> cache = new WeakHashMap<>();

    // Hot events repeat the same class: the last-used entry is a lock-free fast path.
    private volatile Last<S> last;

    /** Creates a cache of the handlers that {@code all} lists, chosen by {@code matches} and sorted by {@code order}. */
    public HandlerCache(
            @NotNull Supplier<? extends Iterable<S>> all,
            @NotNull BiPredicate<S, Class<?>> matches,
            @NotNull Comparator<S> order) {
        this.all = Objects.requireNonNull(all, "all");
        this.matches = Objects.requireNonNull(matches, "matches");
        this.order = Objects.requireNonNull(order, "order");
    }

    /** Forgets the lists of every class that {@code changed} matches, or all of them when it is {@code null}. */
    public void invalidate(@Nullable Class<?> changed) {
        synchronized (cache) {
            generation.incrementAndGet();
            last = null;
            if (changed == null) {
                cache.clear();
            } else {
                cache.keySet().removeIf(changed::isAssignableFrom);
            }
        }
    }

    /** The handlers that match {@code type}, in order. */
    @NotNull
    public List<S> handlersFor(@NotNull Class<?> type) {
        Last<S> fast = last;
        if (fast != null && fast.type == type) {
            return fast.handlers;
        }
        long before = generation.get();
        List<S> cached = cached(type);
        if (cached != null) {
            return cached;
        }
        List<S> built = build(type);
        synchronized (cache) {
            if (generation.get() == before) {
                cache.put(type, built);
                last = new Last<>(type, built);
            }
        }
        return built;
    }

    // Setting `last` inside the lock keeps it consistent with invalidation: a list read just before a
    // change can never be republished as the fast-path entry after the change cleared it.
    private List<S> cached(Class<?> type) {
        synchronized (cache) {
            List<S> found = cache.get(type);
            if (found != null) {
                last = new Last<>(type, found);
            }
            return found;
        }
    }

    private List<S> build(Class<?> type) {
        List<S> found = new ArrayList<>();
        for (S candidate : all.get()) {
            if (matches.test(candidate, type)) {
                found.add(candidate);
            }
        }
        Collections.sort(found, order);
        return Collections.unmodifiableList(found);
    }

    private static final class Last<S> {
        final Class<?> type;
        final List<S> handlers;

        Last(Class<?> type, List<S> handlers) {
            this.type = type;
            this.handlers = handlers;
        }
    }
}
