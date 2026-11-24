package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.audience.feature
import net.kyori.adventure.key.Key
import org.pnfolder.pnlibrary.api.audience.PlayerEffects
import org.pnfolder.pnlibrary.api.audience.PlayerCommands
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.title.Title
import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.audience.Delivery
import org.pnfolder.pnlibrary.api.audience.PlayerEffect
import org.pnfolder.pnlibrary.api.audience.PlayerParticle
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.actions.ActionTarget
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType
import java.time.Duration

/** Writes a warning and carries on when the platform could not deliver; output never fails a scenario. */
private fun ActionContext.warnUnless(delivery: Delivery, what: String): ActionResult {
    when (delivery.status) {
        Delivery.Status.DELIVERED -> Unit
        Delivery.Status.UNSUPPORTED -> logger.warning("$what is not supported by the current platform")
        else -> logger.warning("$what failed: ${delivery.failure?.message ?: delivery.status}")
    }
    return ActionResult.Done
}

/**
 * Sends chat text; the lines become one message, one under another.
 *
 * @property messages text lines; placeholders are filled in
 * @property serializerType text format override, or `null` for the default
 * @property target who receives it
 */
data class MessageAction(
    val messages: List<String> = emptyList(),
    val serializerType: ComponentSerializerType? = null,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        context.target(target).sendMessage(context.component(messages, serializerType))
        return ActionResult.Done
    }
}

/**
 * Shows text above the hotbar.
 *
 * @property text the line; placeholders are filled in
 * @property serializerType text format override, or `null` for the default
 * @property target who sees it
 */
data class ActionBarAction(
    val text: String = "",
    val serializerType: ComponentSerializerType? = null,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        context.target(target).sendActionBar(context.component(text, serializerType))
        return ActionResult.Done
    }
}

/**
 * Shows a large title with a smaller subtitle. Platforms that cannot show titles show nothing.
 *
 * @property title main line
 * @property subtitle second line
 * @property fadeIn time to appear
 * @property stay time fully visible
 * @property fadeOut time to disappear
 * @property serializerType text format override, or `null` for the default
 * @property target who sees it
 */
data class TitleAction(
    val title: String = "",
    val subtitle: String = "",
    val fadeIn: Duration = Duration.ofMillis(500),
    val stay: Duration = Duration.ofMillis(3500),
    val fadeOut: Duration = Duration.ofMillis(1000),
    val serializerType: ComponentSerializerType? = null,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    init {
        require(!fadeIn.isNegative && !stay.isNegative && !fadeOut.isNegative) { "Title times must not be negative" }
    }

    override fun run(context: ActionContext): ActionResult {
        context.target(target).showTitle(
            Title.title(
                context.component(title, serializerType),
                context.component(subtitle, serializerType),
                Title.Times.times(fadeIn, stay, fadeOut),
            ),
        )
        return ActionResult.Done
    }
}

/**
 * Plays a sound.
 *
 * @property key namespaced sound key
 * @property source volume category on the client
 * @property volume relative volume
 * @property pitch pitch multiplier
 * @property target who hears it
 */
data class SoundAction(
    val key: String = "minecraft:entity.experience_orb.pickup",
    val source: Sound.Source = Sound.Source.MASTER,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        context.target(target).playSound(Sound.sound(Key.key(key), source, volume, pitch))
        return ActionResult.Done
    }
}

/** Who runs the command of a [CommandAction]. */
enum class CommandRunner {
    /** The player the scenario is for. */
    PLAYER,

    /** The console, with every permission. */
    CONSOLE,
}

/**
 * Runs a server command. A command the platform refuses fails the scenario, because later steps usually
 * depend on it.
 *
 * @property command command line without the leading slash; placeholders are filled in
 * @property runner who runs it
 */
data class CommandAction(
    val command: String = "",
    @field:ConfigKey("as")
    val runner: CommandRunner = CommandRunner.CONSOLE,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val line = context.format(command).trim().removePrefix("/")
        if (line.isEmpty()) return ActionResult.Done
        return if (context.player.feature<PlayerCommands>()?.runCommand(line, runner == CommandRunner.CONSOLE)?.isDelivered == true) {
            ActionResult.Done
        } else {
            ActionResult.Failed("command.refused", "The platform did not accept the command '$line'")
        }
    }
}

/**
 * Writes a line to the plugin log.
 *
 * @property text the line; placeholders are filled in
 * @property level severity
 */
data class LogAction(
    val text: String = "",
    val level: LogLevel = LogLevel.INFO,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val line = context.format(text)
        when (level) {
            LogLevel.DEBUG -> context.logger.debug(line)
            LogLevel.INFO -> context.logger.info(line)
            LogLevel.SUCCESS -> context.logger.success(line)
            LogLevel.WARNING -> context.logger.warning(line)
            LogLevel.ERROR -> context.logger.error(line)
            LogLevel.CRITICAL -> context.logger.critical(line)
        }
        return ActionResult.Done
    }
}

/**
 * Gives the player a temporary potion effect.
 *
 * @property key effect key
 * @property duration how long it lasts
 * @property amplifier zero-based level, `0` is level I
 * @property ambient whether it counts as ambient
 * @property particles whether its particles show
 * @property icon whether its icon shows
 */
data class EffectAction(
    val key: String = "speed",
    val duration: Duration = Duration.ofSeconds(30),
    val amplifier: Int = 0,
    val ambient: Boolean = true,
    val particles: Boolean = true,
    val icon: Boolean = true,
) : Action {
    override fun run(context: ActionContext): ActionResult = context.warnUnless(
        context.player.feature<PlayerEffects>()?.applyEffect(PlayerEffect(key, duration, amplifier, ambient, particles, icon)) ?: Delivery.unsupported(),
        "Effect $key",
    )
}

/**
 * Spawns particles around the player.
 *
 * @property key particle key
 * @property count how many
 * @property offsetX spread along X
 * @property offsetY spread along Y
 * @property offsetZ spread along Z
 * @property speed speed or particle-specific data
 */
data class ParticleAction(
    val key: String = "flame",
    val count: Int = 1,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    val speed: Double = 0.0,
) : Action {
    override fun run(context: ActionContext): ActionResult = context.warnUnless(
        context.player.feature<PlayerEffects>()?.spawnParticle(PlayerParticle(key, count, offsetX, offsetY, offsetZ, speed)) ?: Delivery.unsupported(),
        "Particle $key",
    )
}
