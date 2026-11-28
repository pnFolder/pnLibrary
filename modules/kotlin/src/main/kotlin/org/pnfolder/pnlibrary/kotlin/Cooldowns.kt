package org.pnfolder.pnlibrary.kotlin

import kotlin.time.Duration
import kotlin.time.toJavaDuration
import org.pnfolder.pnlibrary.api.cooldowns.Cooldown
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService

/**
 * A named cooldown with the builder as `this` and Kotlin durations:
 *
 * ```
 * val kit = cooldowns.define("kit") {
 *     duration(30.minutes)
 *     tier("vip", 10.minutes)
 *     bypass("kit.bypass")
 * }
 * ```
 */
public fun CooldownService.define(action: String, block: Cooldown.Builder.() -> Unit): Cooldown =
    cooldown(action) { it.block() }

/** The wait of the cooldown, a Kotlin duration such as `30.seconds`. */
public fun Cooldown.Builder.duration(value: Duration): Cooldown.Builder = duration(value.toJavaDuration())

/** A player who holds [permission] waits [value] instead. */
public fun Cooldown.Builder.tier(permission: String, value: Duration): Cooldown.Builder = tier(permission, value.toJavaDuration())
