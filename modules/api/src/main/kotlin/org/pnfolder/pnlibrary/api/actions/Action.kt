package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.actions.builtin.ActionBarAction
import org.pnfolder.pnlibrary.api.actions.builtin.CommandAction
import org.pnfolder.pnlibrary.api.actions.builtin.DelayAction
import org.pnfolder.pnlibrary.api.actions.builtin.EffectAction
import org.pnfolder.pnlibrary.api.actions.builtin.GroupAction
import org.pnfolder.pnlibrary.api.actions.builtin.IfAction
import org.pnfolder.pnlibrary.api.actions.builtin.LogAction
import org.pnfolder.pnlibrary.api.actions.builtin.MessageAction
import org.pnfolder.pnlibrary.api.actions.builtin.ParticleAction
import org.pnfolder.pnlibrary.api.actions.builtin.RepeatAction
import org.pnfolder.pnlibrary.api.actions.builtin.RequireAction
import org.pnfolder.pnlibrary.api.actions.builtin.RunAction
import org.pnfolder.pnlibrary.api.actions.builtin.SoundAction
import org.pnfolder.pnlibrary.api.actions.builtin.StopAction
import org.pnfolder.pnlibrary.api.actions.builtin.TitleAction
import org.pnfolder.pnlibrary.api.actions.internal.ActionText
import org.pnfolder.pnlibrary.api.actions.internal.ActionNode
import org.pnfolder.pnlibrary.api.config.ConfigNodeForm
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic
import org.pnfolder.pnlibrary.api.config.ConfigTextForm
import org.pnfolder.pnlibrary.api.config.ConfigType
import org.pnfolder.pnlibrary.api.config.ConfigTypes

@ConfigPolymorphic(discriminator = "type")
@ConfigTextForm(ActionText::class)
@ConfigNodeForm(ActionNode::class)
@ConfigTypes(
    ConfigType(MessageAction::class, "message", aliases = ["messages", "msg"]),
    ConfigType(ActionBarAction::class, "actionbar", aliases = ["action-bar"]),
    ConfigType(TitleAction::class, "title"),
    ConfigType(SoundAction::class, "sound"),
    ConfigType(CommandAction::class, "command", aliases = ["cmd"]),
    ConfigType(LogAction::class, "log"),
    ConfigType(EffectAction::class, "effect", aliases = ["potion-effect"]),
    ConfigType(ParticleAction::class, "particle", aliases = ["particles"]),
    ConfigType(DelayAction::class, "delay", aliases = ["wait", "pause", "later"]),
    ConfigType(IfAction::class, "if", aliases = ["when"]),
    ConfigType(RequireAction::class, "require"),
    ConfigType(RepeatAction::class, "repeat"),
    ConfigType(GroupAction::class, "group", aliases = ["sequence"]),
    ConfigType(RunAction::class, "run", aliases = ["template"]),
    ConfigType(StopAction::class, "stop", aliases = ["return"]),
)
/**
 * One executable operation in a configured action graph.
 *
 * Actions are polymorphic configuration values. The `type` discriminator selects
 * one of the built-in implementations declared by [ConfigTypes], while plugins may
 * publish additional implementations through the configuration type registry.
 * Implementations execute synchronously unless their contract explicitly delegates
 * work to [ActionContext.tasks], as [DelayAction] does.
 *
 * An action should use only services exposed by [ActionContext]. This keeps action
 * definitions independent from a particular server platform and makes one parsed
 * action reusable for multiple executions.
 */
fun interface Action {
    /**
     * Executes this operation using the data and plugin-owned services in [context].
     *
     * Implementations may throw when their configuration is invalid or a required
     * context object is absent. Callers that execute user-authored action graphs
     * should define their own failure-reporting boundary around this method.
     */
    fun execute(context: ActionContext)
}
