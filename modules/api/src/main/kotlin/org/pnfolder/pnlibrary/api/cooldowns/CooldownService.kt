package org.pnfolder.pnlibrary.api.cooldowns

import org.jetbrains.annotations.ApiStatus
import java.time.Duration
import java.util.UUID
import java.util.function.Consumer

/**
 * Plugin-scoped, thread-safe cooldown storage based on monotonic elapsed time.
 *
 * Action names are trimmed and compared case-insensitively. Durations must not be
 * negative; a zero duration removes or immediately expires an entry. Methods that read
 * or create cooldowns fail after [close], while cleanup operations remain idempotent. Expired entries are dropped
 * as the service is used, so a subject that is never asked again does not stay in memory.
 */
@ApiStatus.NonExtendable
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

    /** Removes every entry of [subject], such as when a player leaves; returns how many were removed. */
    fun clear(subject: UUID): Int

    /** The active cooldowns of [subject] by action with the time left, longest first; expired ones are not listed. */
    fun active(subject: UUID): Map<String, Duration>

    /**
     * A named cooldown of fixed [duration] for [action]. A player holding [bypassPermission] is never held back; the
     * console never is either.
     */
    fun cooldown(action: String, duration: Duration, bypassPermission: String? = null): Cooldown

    /** A named cooldown with everything [Cooldown.Builder] offers: tiers, your own reactions, one shared timer. */
    fun cooldown(action: String, configure: Consumer<Cooldown.Builder>): Cooldown

    /** Number of active entries after opportunistic expiry cleanup. */
    val size: Int
}
