package ru.privatenull.pnlibrary.api.cooldowns

import java.time.Duration
import java.util.UUID

data class CooldownResult(val allowed: Boolean, val remaining: Duration)

/** Plugin-scoped, monotonic cooldown storage safe against wall-clock changes. */
interface CooldownService : AutoCloseable {
    fun acquire(subject: UUID, action: String, duration: Duration): CooldownResult
    fun has(subject: UUID, action: String): Boolean
    fun remaining(subject: UUID, action: String): Duration
    fun set(subject: UUID, action: String, duration: Duration)
    fun extend(subject: UUID, action: String, duration: Duration): Duration
    fun reset(subject: UUID, action: String): Boolean
    fun clear()
    val size: Int
}
