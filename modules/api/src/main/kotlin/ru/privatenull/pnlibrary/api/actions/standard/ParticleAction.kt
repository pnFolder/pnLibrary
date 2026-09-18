package ru.privatenull.pnlibrary.api.actions

/**
 * Spawns particles around the player that triggered the action.
 *
 * The action is intentionally small and delegates particle lookup to
 * [LibraryPlayer.spawnParticle] so platform modules can handle version-specific
 * names and native particle data.
 *
 * @property key namespaced or platform-native particle key
 * @property count number of particles to spawn
 * @property offsetX maximum horizontal X-axis spread
 * @property offsetY maximum vertical Y-axis spread
 * @property offsetZ maximum horizontal Z-axis spread
 * @property speed particle speed or additional particle-specific data
 */
data class ParticleAction(
    val key: String = "flame",
    val count: Int = 1,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    val speed: Double = 0.0,
) : Action {
    override fun execute(context: ActionContext) {
        val particle = PlayerParticle(key, count, offsetX, offsetY, offsetZ, speed)
        if (!context.player.spawnParticle(particle)) {
            context.logger.warning("Particle action is unsupported by the current platform: $key")
        }
    }
}

