package ru.privatenull.pnlibrary.api.actions

import java.time.Duration

/**
 * Applies a temporary status effect to the player that triggered the action.
 *
 * The action delegates platform-specific effect resolution to
 * [LibraryPlayer.applyEffect]. If the platform adapter cannot resolve or apply
 * the effect, execution continues and a warning is written to the action logger.
 *
 * @property key namespaced or platform-native effect key
 * @property duration amount of time the effect should stay active
 * @property amplifier zero-based effect amplifier, where `0` means level I
 * @property ambient whether the effect should be marked as ambient
 * @property particles whether regular effect particles should be visible
 * @property icon whether the client-side effect icon should be visible
 */
data class EffectAction(
    val key: String = "speed",
    val duration: Duration = Duration.ofSeconds(30),
    val amplifier: Int = 0,
    val ambient: Boolean = true,
    val particles: Boolean = true,
    val icon: Boolean = true,
) : Action {
    override fun execute(context: ActionContext) {
        val effect = PlayerEffect(key, duration, amplifier, ambient, particles, icon)
        if (!context.player.applyEffect(effect)) {
            context.logger.warning("Effect action is unsupported by the current platform: $key")
        }
    }
}

