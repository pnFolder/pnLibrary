package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.actions.builtin.ActionBarAction
import org.pnfolder.pnlibrary.api.actions.builtin.AttemptAction
import org.pnfolder.pnlibrary.api.actions.builtin.CommandAction
import org.pnfolder.pnlibrary.api.actions.builtin.DelayAction
import org.pnfolder.pnlibrary.api.actions.builtin.EffectAction
import org.pnfolder.pnlibrary.api.actions.builtin.IfAction
import org.pnfolder.pnlibrary.api.actions.builtin.LogAction
import org.pnfolder.pnlibrary.api.actions.builtin.MessageAction
import org.pnfolder.pnlibrary.api.actions.builtin.ParticleAction
import org.pnfolder.pnlibrary.api.actions.builtin.RepeatAction
import org.pnfolder.pnlibrary.api.actions.builtin.RequireAction
import org.pnfolder.pnlibrary.api.actions.builtin.SoundAction
import org.pnfolder.pnlibrary.api.actions.builtin.CallAction
import org.pnfolder.pnlibrary.api.actions.builtin.RandomAction
import org.pnfolder.pnlibrary.api.actions.builtin.SetAction
import org.pnfolder.pnlibrary.api.actions.builtin.StopAction
import org.pnfolder.pnlibrary.api.actions.builtin.TitleAction
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic
import org.pnfolder.pnlibrary.api.config.ConfigType
import org.pnfolder.pnlibrary.api.config.ConfigTypes

/**
 * One step of a configured scenario, such as sending a message, playing a sound or taking money.
 *
 * A scenario is a list of actions written in a configuration file; each action has a `type`.
 * Actions run in order. Each one reports an [ActionResult]: the list goes on after [ActionResult.Done] and ends
 * early after [ActionResult.Stopped] (a choice) or [ActionResult.Failed] (an error that undoes earlier steps).
 *
 * ```yaml
 * on-buy:
 *   - type: require                 # stops quietly when the requirement is not met
 *     when: { type: permission, permission: shop.use }
 *     deny:
 *       - { type: message, messages: ['&cNo permission'] }
 *   - type: attempt                 # a failed charge shows a message instead of an error
 *     do:
 *       - { type: charge, currency: money, amount: 100 }
 *       - { type: command, command: 'give [player] diamond 1' }
 *     fail:
 *       - { type: message, messages: ['&cCannot pay: [error]'] }
 * ```
 *
 * Plugins may add their own types through the configuration type registry. In code an action is a lambda:
 *
 * ```kotlin
 * val greet = Action { ctx ->
 *     ctx.player.sendMessage(Component.text("Hello, ${ctx.player.name}"))
 *     ActionResult.Done
 * }
 * ```
 *
 * An action must keep to the services of [ActionContext] so that one parsed list can run many times, on any
 * platform. It should report a problem as [ActionResult.Failed] rather than throw; a thrown exception is
 * turned into a failure by the context.
 */
@ConfigPolymorphic(discriminator = "type")
@ConfigTypes(
    ConfigType(MessageAction::class, "message", aliases = ["messages"]),
    ConfigType(ActionBarAction::class, "actionbar", aliases = ["action-bar"]),
    ConfigType(TitleAction::class, "title"),
    ConfigType(SoundAction::class, "sound"),
    ConfigType(CommandAction::class, "command"),
    ConfigType(LogAction::class, "log"),
    ConfigType(EffectAction::class, "effect"),
    ConfigType(ParticleAction::class, "particle"),
    ConfigType(DelayAction::class, "delay", aliases = ["wait"]),
    ConfigType(RepeatAction::class, "repeat"),
    ConfigType(IfAction::class, "if"),
    ConfigType(RequireAction::class, "require"),
    ConfigType(AttemptAction::class, "attempt"),
    ConfigType(StopAction::class, "stop"),
    ConfigType(SetAction::class, "set"),
    ConfigType(CallAction::class, "call"),
    ConfigType(RandomAction::class, "random"),
)
fun interface Action {
    /** Performs the step with the data and services in [context] and reports how it went. */
    fun run(context: ActionContext): ActionResult
}
