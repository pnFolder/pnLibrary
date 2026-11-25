package org.pnfolder.pnlibrary.api.cooldowns

import org.jetbrains.annotations.ApiStatus
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownStarted
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownDenied
import org.pnfolder.pnlibrary.api.cooldowns.builder.TierPick
import net.kyori.adventure.audience.Audience
import java.time.Duration
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * One cooldown with its name, length and behavior fixed once, so the call site says what it asks and nothing else. Get it
 * from [CooldownService.cooldown]. The console has no cooldown, and neither has a player who holds the bypass permission.
 *
 * The simple way, with the library's own answer to a player who comes too soon:
 *
 * ```kotlin
 * val home = context.cooldowns.cooldown("home", Duration.ofSeconds(30), bypassPermission = "shop.home.nocooldown")
 * execute { ctx ->
 *     home.require(ctx.sender)          // too soon: the command stops and the player reads how long to wait
 *     teleportHome(ctx.sender)
 * }
 * ```
 *
 * Your own message and your own actions, a shorter wait for donors, one timer for everybody:
 *
 * ```kotlin
 * val kit = context.cooldowns.cooldown("kit") { c ->
 *     c.duration(Duration.ofHours(1))
 *     c.tier("shop.donor", Duration.ofMinutes(20))        // shorter for donors
 *     c.tier(listOf("shop.newbie", "shop.trial"), 2.0)    // twice as long for either group
 *     c.bypass("shop.admin", "shop.staff")                // any one of them: no wait at all
 *     c.onDenied { d -> d.who.sendActionBar(Component.text("Kit again in ${d.remainingSeconds} s")) }
 *     c.onStarted { s -> logger.info("${s.who.name} took the kit, next in ${s.duration}") }
 * }
 * kit.run(player) { giveKit(player) }                     // runs only when allowed, otherwise your onDenied
 * ```
 */
@ApiStatus.NonExtendable
interface Cooldown {
    /** The action name this cooldown is kept under. */
    val action: String

    /** How long the cooldown lasts when no tier applies. */
    val duration: Duration

    /** How long the cooldown lasts for [who]: the tier [who] holds (see [TierPick]), otherwise [duration]. */
    fun durationFor(who: Audience): Duration

    /** Starts the cooldown for [subject] unless it is still running; see [CooldownService.acquire]. */
    fun acquire(subject: UUID): CooldownResult

    /**
     * Starts the cooldown for [who] unless it is still running. Always allowed for the console and for a player with the
     * bypass permission. Calls the `onStarted` handler when it starts; never calls `onDenied`.
     */
    fun acquire(who: Audience): CooldownResult

    /** Runs [action] and starts the cooldown, or, when [who] has to wait, calls the `onDenied` handler (or the library's own answer) and skips [action]. */
    fun run(who: Audience, action: Runnable): CooldownResult

    /** Starts the cooldown for [who], or stops the command with an [org.pnfolder.pnlibrary.api.commands.error.OnCooldown] answer holding the time left. */
    fun require(who: Audience) {
        acquire(who).orThrow()
    }

    /** The time left for [who], or [Duration.ZERO] when it is not running or [who] has no cooldown. */
    fun remaining(who: Audience): Duration

    /** Whether [who] may act now; nothing is started. */
    fun isReady(who: Audience): Boolean = remaining(who).isZero

    /** Ends the cooldown for [subject] and says whether it was running. */
    fun reset(subject: UUID): Boolean

    /** Ends the cooldown for [who] and says whether it was running. */
    fun reset(who: Audience): Boolean

    /** The settings of a [Cooldown]; every call returns the builder, and unset parts keep their defaults. */
    interface Builder {
        /** How long the cooldown lasts when no tier applies; required, and not negative. */
        fun duration(value: Duration): Builder

        /** A player who holds any one of [permissions] never waits; the console never does either. Calls add up. */
        fun bypass(vararg permissions: String): Builder

        /** A player for whom [condition] is true never waits, for what a permission cannot say (a world, a rank, a state). Calls add up. */
        fun bypassIf(condition: Predicate<Audience>): Builder

        /** A player who holds [permission] waits [value] instead, longer or shorter than [duration]. */
        fun tier(permission: String, value: Duration): Builder

        /** A player who holds any one of [permissions] waits [value] instead. */
        fun tier(permissions: Collection<String>, value: Duration): Builder

        /** A player who holds [permission] waits [duration] times [factor]: `0.5` halves the wait, `2.0` doubles it. Not negative. */
        fun tier(permission: String, factor: Double): Builder

        /** A player who holds any one of [permissions] waits [duration] times [factor]. */
        fun tier(permissions: Collection<String>, factor: Double): Builder

        /** Which tier applies when a player holds several: [TierPick.FIRST] by default. */
        fun pick(pick: TierPick): Builder

        /** One timer for everybody instead of one per player: whoever uses it first holds everyone else back. */
        fun shared(): Builder

        /** What to do when someone is held back by [Cooldown.run]; replaces the library's own message. */
        fun onDenied(handler: Consumer<CooldownDenied>): Builder

        /** What to do each time a cooldown starts. */
        fun onStarted(handler: Consumer<CooldownStarted>): Builder
    }
}
