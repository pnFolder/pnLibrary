package org.pnfolder.pnlibrary.core.events

import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

// Invalidating T drops only entries for classes T matches, so churn on one event never slows others.
// Lists built during a change are returned but not cached. Weak keys allow plugin class GC.
class HandlerCache<S : Any>(
    private val all: () -> Sequence<S>,
    private val matches: (S, Class<*>) -> Boolean,
    private val order: Comparator<S>,
) {
    private val generation = AtomicLong()
    private val cache = WeakHashMap<Class<*>, List<S>>()

    // Hot events repeat the same class — the last-used entry is a lock-free fast path.
    private class Last<S>(val type: Class<*>, val handlers: List<S>)

    @Volatile private var last: Last<S>? = null

    fun invalidate(changed: Class<*>?) {
        synchronized(cache) {
            generation.incrementAndGet()
            last = null
            if (changed == null) cache.clear() else cache.keys.removeAll { changed.isAssignableFrom(it) }
        }
    }

    fun handlersFor(type: Class<*>): List<S> {
        last?.let { if (it.type === type) return it.handlers }
        val before = generation.get()
        // Setting `last` inside the lock keeps it consistent with invalidation: a list read just before a
        // change can never be republished as the fast-path entry after the change cleared it.
        synchronized(cache) { cache[type]?.also { last = Last(type, it) } }?.let { return it }
        val built = all()
            .filter { matches(it, type) }
            .sortedWith(order)
            .toList()
        synchronized(cache) {
            if (generation.get() == before) {
                cache[type] = built
                last = Last(type, built)
            }
        }
        return built
    }
}
