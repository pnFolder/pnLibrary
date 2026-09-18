package ru.privatenull.pnlibrary.api.cooldowns

import java.time.Duration
import java.util.UUID

/**
 * Result of an atomic cooldown acquisition attempt.
 *
 * @property allowed whether the caller acquired a new cooldown
 * @property remaining time left on the existing cooldown when denied, otherwise zero
 */
data class CooldownResult(
    val allowed: Boolean,
    val remaining: Duration,
)

/**
 * Plugin-scoped, thread-safe cooldown storage based on monotonic elapsed time.
 *
 * Action names are trimmed and compared case-insensitively. Durations must not be
 * negative; a zero duration removes or immediately expires an entry. Methods that read
 * or create cooldowns fail after [close], while cleanup operations remain idempotent.
 */
interface CooldownService : AutoCloseable {
    /** Atomically creates a cooldown unless an unexpired entry already exists. */
    fun acquire(subject: UUID, action: String, duration: Duration): CooldownResult
    /** Returns whether [subject] currently has an active cooldown for [action]. */
    fun has(subject: UUID, action: String): Boolean
    /** Returns the non-negative remaining duration, or [Duration.ZERO] when inactive. */
    fun remaining(subject: UUID, action: String): Duration
    /** Replaces the deadline; a zero [duration] removes the entry. */
    fun set(subject: UUID, action: String, duration: Duration)
    /** Extends from the current deadline, or from now when no active entry exists. */
    fun extend(subject: UUID, action: String, duration: Duration): Duration
    /** Removes one entry and reports whether an entry was present. */
    fun reset(subject: UUID, action: String): Boolean
    /** Removes every entry owned by this service. */
    fun clear()
    /** Number of active entries after opportunistic expiry cleanup. */
    val size: Int
}
