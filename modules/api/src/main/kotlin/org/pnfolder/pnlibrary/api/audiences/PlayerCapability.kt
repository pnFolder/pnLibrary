package org.pnfolder.pnlibrary.api.audiences

/**
 * An operation a platform player adapter may or may not support, listed in [LibraryPlayer.capabilities].
 *
 * Check it before relying on an optional operation:
 *
 * ```kotlin
 * if (PlayerCapability.PARTICLE in player.capabilities) player.spawnParticle(PlayerParticle("flame", count = 10))
 * ```
 */
enum class PlayerCapability {
    MESSAGE,
    ACTION_BAR,
    SOUND,
    EFFECT,
    PARTICLE,
}
