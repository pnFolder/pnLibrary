package org.pnfolder.pnlibrary.core.cooldowns

import net.kyori.adventure.audience.Audience
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownDenied
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownStarted
import org.pnfolder.pnlibrary.api.cooldowns.builder.TierPick
import java.time.Duration
import java.util.function.Consumer
import java.util.function.Predicate

/** Everything a [NamedCooldown] was configured with. */
internal data class CooldownSettings(
    val duration: Duration,
    val bypassPermissions: List<String> = emptyList(),
    val bypassConditions: List<Predicate<Audience>> = emptyList(),
    val tiers: List<CooldownTier> = emptyList(),
    val pick: TierPick = TierPick.FIRST,
    val shared: Boolean = false,
    val onDenied: Consumer<CooldownDenied>? = null,
    val onStarted: Consumer<CooldownStarted>? = null,
)
