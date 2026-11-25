package org.pnfolder.pnlibrary.api.cooldowns.builder

import org.pnfolder.pnlibrary.api.cooldowns.Cooldown
import net.kyori.adventure.audience.Audience
import java.time.Duration

/**
 * What a [Cooldown.Builder.onStarted] handler is told when a cooldown begins.
 *
 * @property who the one it started for
 * @property action the name of the cooldown
 * @property duration how long it lasts for this one, after tiers
 */
data class CooldownStarted(val who: Audience, val action: String, val duration: Duration)
