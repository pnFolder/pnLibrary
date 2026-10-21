package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.config.ConfigNodeForm
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic
import org.pnfolder.pnlibrary.api.config.ConfigTextForm
import org.pnfolder.pnlibrary.api.config.ConfigType
import org.pnfolder.pnlibrary.api.config.ConfigTypes

@ConfigPolymorphic(discriminator = "type")
@ConfigTextForm(ActionLine::class)
@ConfigNodeForm(ActionNode::class)
@ConfigTypes(
    ConfigType(MessageAction::class, "message", aliases = ["messages", "msg"]),
    ConfigType(ActionBarAction::class, "action-bar", aliases = ["actionbar"]),
    ConfigType(SoundAction::class, "sound"),
    ConfigType(ConsoleLogAction::class, "console", aliases = ["log"]),
    ConfigType(DelayAction::class, "delay", aliases = ["later"]),
    ConfigType(ConditionalAction::class, "if", aliases = ["when", "condition"]),
    ConfigType(EffectAction::class, "effect", aliases = ["potion-effect"]),
    ConfigType(ParticleAction::class, "particle", aliases = ["particles"]),
    ConfigType(SequenceAction::class, "sequence", aliases = ["group"]),
    ConfigType(SetValueAction::class, "set-value", aliases = ["set"]),
    ConfigType(CopyValueAction::class, "copy-value", aliases = ["copy"]),
    ConfigType(RemoveValueAction::class, "remove-value", aliases = ["unset"]),
    ConfigType(SwitchAction::class, "switch", aliases = ["match"]),
    ConfigType(NoOpAction::class, "noop", aliases = ["ignore", "silent"]),
    ConfigType(CommandAction::class, "command", aliases = ["cmd"]),
    ConfigType(StopAction::class, "stop", aliases = ["return"]),
    ConfigType(WaitAction::class, "wait", aliases = ["pause"]),
    ConfigType(RepeatAction::class, "repeat"),
    ConfigType(TitleAction::class, "title"),
    ConfigType(RunAction::class, "run", aliases = ["template"]),
    ConfigType(RequirementsAction::class, "requirements", aliases = ["require", "check"]),
    ConfigType(UpdatePlaceholderAction::class, "update-placeholder", aliases = ["write-placeholder"]),
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
