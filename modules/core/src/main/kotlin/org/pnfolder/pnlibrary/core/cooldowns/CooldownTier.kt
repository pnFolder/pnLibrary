package org.pnfolder.pnlibrary.core.cooldowns

import java.time.Duration

/** The wait for those who hold any of [permissions]: a fixed [length], or the base length times [factor]. */
internal class CooldownTier(val permissions: List<String>, private val length: Duration?, private val factor: Double?) {
    init {
        require(permissions.isNotEmpty()) { "A tier needs at least one permission" }
        require(length != null || factor != null) { "A tier needs a length or a factor" }
        require(length == null || !length.isNegative) { "Cooldown duration cannot be negative" }
        require(factor == null || (factor >= 0.0 && factor.isFinite())) { "A tier factor cannot be negative" }
    }

    fun lengthFor(base: Duration): Duration = length ?: scaled(base, factor!!)

    private fun scaled(base: Duration, by: Double): Duration {
        val nanos = base.toNanos() * by
        return if (nanos >= Long.MAX_VALUE) Duration.ofNanos(Long.MAX_VALUE) else Duration.ofNanos(nanos.toLong())
    }
}
