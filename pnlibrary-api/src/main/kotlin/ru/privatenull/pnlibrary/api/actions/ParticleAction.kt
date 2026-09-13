package ru.privatenull.pnlibrary.api.actions

/** Emits a particle around the current player. */
data class ParticleAction(
    val key: String = "flame",
    val count: Int = 1,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    val speed: Double = 0.0,
) : Action {
    override fun execute(context: ActionContext) {
        require(count >= 0) { "Particle count must not be negative" }
        if (!context.player.spawnParticle(PlayerParticle(key, count, offsetX, offsetY, offsetZ, speed))) {
            context.logger.warning("Particle action is unsupported by the current platform: $key")
        }
    }
}




