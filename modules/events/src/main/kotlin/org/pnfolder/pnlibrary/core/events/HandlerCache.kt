package org.pnfolder.pnlibrary.core.events

import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Sorted listeners per event class. Adding or closing a subscription for type T drops only the cached
 * lists of classes that T matches, so churn on one event never slows the others. A list built while a
 * change was happening is returned for that call but not cached. Weak keys let an unloaded plugin's
 * event classes be collected when nothing listens to them.
 */
class HandlerCache<S : Any>(
    private val all: () -> Sequence<S>,
    private val matches: (S, Class<*>) -> Boolean,
    private val order: Comparator<S>,
) {
    private val generation = AtomicLong()
    private val cache = WeakHashMap<Class<*>, List<S>>()

    /** The most recently used lookup, readable without a lock: hot events repeat the same class. */
    private class Last<S>(val type: Class<*>, val handlers: List<S>)

    @Volatile private var last: Last<S>? = null

    /** Forgets cached lists affected by a subscription to [changed]; `null` forgets everything. */
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
