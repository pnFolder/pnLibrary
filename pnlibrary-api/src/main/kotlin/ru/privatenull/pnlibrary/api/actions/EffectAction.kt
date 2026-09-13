package ru.privatenull.pnlibrary.api.actions

import java.time.Duration

/** Applies a potion/status effect to the current player. */
data class EffectAction(
    val key: String = "speed",
    val duration: Duration = Duration.ofSeconds(30),
    val amplifier: Int = 0,
    val ambient: Boolean = true,
    val particles: Boolean = true,
    val icon: Boolean = true,
) : Action {
    override fun execute(context: ActionContext) {
        require(!duration.isNegative && !duration.isZero) { "Effect duration must be positive" }
        require(amplifier >= 0) { "Effect amplifier must not be negative" }
        if (!context.player.applyEffect(PlayerEffect(key, duration, amplifier, ambient, particles, icon))) {
            context.logger.warning("Effect action is unsupported by the current platform: $key")
        }
    }
}


