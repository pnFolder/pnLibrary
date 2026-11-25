package org.pnfolder.pnlibrary.core.cooldowns

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audience.hasPermission
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.cooldowns.Cooldown
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownDenied
import org.pnfolder.pnlibrary.api.cooldowns.CooldownResult
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownStarted
import org.pnfolder.pnlibrary.api.cooldowns.builder.TierPick
import java.time.Duration
import java.util.UUID

/** A [Cooldown] over [service], kept under one action name and behaving as [settings] say. */
internal class NamedCooldown(
    private val service: CooldownService,
    override val action: String,
    private val settings: CooldownSettings,
) : Cooldown {
    override val duration: Duration get() = settings.duration

    override fun durationFor(who: Audience): Duration {
        val held = settings.tiers.filter { tier -> tier.permissions.any(who::hasPermission) }.map { it.lengthFor(settings.duration) }
        if (held.isEmpty()) return settings.duration
        return when (settings.pick) {
            TierPick.FIRST -> held.first()
            TierPick.SHORTEST -> held.min()
            TierPick.LONGEST -> held.max()
        }
    }

    override fun acquire(subject: UUID): CooldownResult = service.acquire(key(subject), action, settings.duration)

    override fun acquire(who: Audience): CooldownResult {
        val subject = subjectOf(who) ?: return CooldownResult.allowed()
        if (bypasses(who)) return CooldownResult.allowed()
        val length = durationFor(who)
        val result = service.acquire(key(subject), action, length)
        if (result.allowed) settings.onStarted?.accept(CooldownStarted(who, action, length))
        return result
    }

    override fun run(who: Audience, action: Runnable): CooldownResult {
        val result = acquire(who)
        if (result.allowed) action.run() else deny(who, result)
        return result
    }

    override fun remaining(who: Audience): Duration {
        val subject = subjectOf(who) ?: return Duration.ZERO
        if (bypasses(who)) return Duration.ZERO
        return service.remaining(key(subject), action)
    }

    override fun reset(subject: UUID): Boolean = service.reset(key(subject), action)

    override fun reset(who: Audience): Boolean = subjectOf(who)?.let(::reset) ?: false

    private fun deny(who: Audience, result: CooldownResult) {
        val denied = CooldownDenied(who, action, result.remaining)
        val handler = settings.onDenied
        if (handler != null) handler.accept(denied)
        else who.sendMessage(Component.text("Подождите ещё ${denied.remainingSeconds} с.", ChatPalette.TEXT))
    }

    private fun bypasses(who: Audience) =
        settings.bypassPermissions.any(who::hasPermission) || settings.bypassConditions.any { it.test(who) }

    private fun key(subject: UUID): UUID = if (settings.shared) SHARED else subject

    private fun subjectOf(who: Audience): UUID? = who.get(Identity.UUID).orElse(null)

    private companion object {
        val SHARED = UUID(0, 0)
    }
}
