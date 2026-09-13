package ru.privatenull.pnlibrary.api.actions

import java.time.Duration

data class PlayerEffect(val key: String, val duration: Duration = Duration.ofSeconds(30), val amplifier: Int = 0, val ambient: Boolean = true, val particles: Boolean = true, val icon: Boolean = true)
data class PlayerParticle(val key: String, val count: Int = 1, val offsetX: Double = 0.0, val offsetY: Double = 0.0, val offsetZ: Double = 0.0, val speed: Double = 0.0)
