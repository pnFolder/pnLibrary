package ru.privatenull.pnlibrary.core.cooldowns

import ru.privatenull.pnlibrary.api.cooldowns.CooldownResult
import ru.privatenull.pnlibrary.api.cooldowns.CooldownService
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class CooldownServiceImpl : CooldownService {
    private data class Key(val subject: UUID, val action: String)
    private val deadlines = ConcurrentHashMap<Key, Long>()
    private val closed = AtomicBoolean(false)
    override val size: Int get() { cleanup(); return deadlines.size }

    override fun acquire(subject: UUID, action: String, duration: Duration): CooldownResult {
        validate(action, duration)
        checkOpen()
        val key = Key(subject, action.lowercase())
        val now = System.nanoTime()
        while (true) {
            val current = deadlines[key]
            if (current != null && current > now) return CooldownResult(false, nanos(current - now))
            val target = deadline(now, duration)
            if (current == null && deadlines.putIfAbsent(key, target) == null) return CooldownResult(true, Duration.ZERO)
            if (current != null && deadlines.replace(key, current, target)) return CooldownResult(true, Duration.ZERO)
        }
    }

    override fun has(subject: UUID, action: String): Boolean = !remaining(subject, action).isZero
    override fun remaining(subject: UUID, action: String): Duration {
        checkOpen()
        val key = Key(subject, normalized(action))
        val deadline = deadlines[key] ?: return Duration.ZERO
        val left = deadline - System.nanoTime()
        if (left <= 0) { deadlines.remove(key, deadline); return Duration.ZERO }
        return nanos(left)
    }
    override fun set(subject: UUID, action: String, duration: Duration) {
        validate(action, duration); checkOpen()
        if (duration.isZero) deadlines.remove(Key(subject, normalized(action)))
        else deadlines[Key(subject, normalized(action))] = deadline(System.nanoTime(), duration)
    }
    override fun extend(subject: UUID, action: String, duration: Duration): Duration {
        validate(action, duration); checkOpen()
        val key = Key(subject, normalized(action)); val now = System.nanoTime()
        val target = deadlines.compute(key) { _, old -> deadline(maxOf(now, old ?: now), duration) }!!
        return nanos(target - now)
    }
    override fun reset(subject: UUID, action: String) = deadlines.remove(Key(subject, normalized(action))) != null
    override fun clear() = deadlines.clear()
    override fun close() { if (closed.compareAndSet(false, true)) clear() }
    private fun cleanup() { val now = System.nanoTime(); deadlines.entries.removeIf { it.value <= now } }
    private fun checkOpen() = check(!closed.get()) { "Cooldown service is closed" }
    private fun normalized(action: String) = action.trim().lowercase().also { require(it.isNotEmpty()) { "Cooldown action cannot be blank" } }
    private fun validate(action: String, duration: Duration) { normalized(action); require(!duration.isNegative) { "Cooldown duration cannot be negative" } }
    private fun deadline(base: Long, duration: Duration): Long = try { Math.addExact(base, duration.toNanos()) } catch (_: ArithmeticException) { Long.MAX_VALUE }
    private fun nanos(value: Long) = Duration.ofNanos(value.coerceAtLeast(0))
}
