package org.pnfolder.pnlibrary.core.cooldowns

import net.kyori.adventure.audience.Audience
import org.pnfolder.pnlibrary.api.cooldowns.Cooldown
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownDenied
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownStarted
import org.pnfolder.pnlibrary.api.cooldowns.builder.TierPick
import java.time.Duration
import java.util.function.Consumer
import java.util.function.Predicate

internal class CooldownBuilder : Cooldown.Builder {
    private var duration: Duration? = null
    private val bypassPermissions = mutableListOf<String>()
    private val bypassConditions = mutableListOf<Predicate<Audience>>()
    private val tiers = mutableListOf<CooldownTier>()
    private var pick = TierPick.FIRST
    private var shared = false
    private var denied: Consumer<CooldownDenied>? = null
    private var started: Consumer<CooldownStarted>? = null

    override fun duration(value: Duration) = apply { duration = value }

    override fun bypass(vararg permissions: String) = apply { bypassPermissions += permissions }

    override fun bypassIf(condition: Predicate<Audience>) = apply { bypassConditions += condition }

    override fun tier(permission: String, value: Duration) = tier(listOf(permission), value)

    override fun tier(permissions: Collection<String>, value: Duration) = apply { tiers += CooldownTier(permissions.toList(), value, null) }

    override fun tier(permission: String, factor: Double) = tier(listOf(permission), factor)

    override fun tier(permissions: Collection<String>, factor: Double) = apply { tiers += CooldownTier(permissions.toList(), null, factor) }

    override fun pick(pick: TierPick) = apply { this.pick = pick }

    override fun shared() = apply { shared = true }

    override fun onDenied(handler: Consumer<CooldownDenied>) = apply { denied = handler }

    override fun onStarted(handler: Consumer<CooldownStarted>) = apply { started = handler }

    fun build(service: CooldownService, action: String): Cooldown {
        val base = requireNotNull(duration) { "Cooldown '$action' needs a duration" }
        require(!base.isNegative) { "Cooldown duration cannot be negative" }
        val settings = CooldownSettings(base, bypassPermissions.toList(), bypassConditions.toList(), tiers.toList(), pick, shared, denied, started)
        return NamedCooldown(service, action, settings)
    }
}
