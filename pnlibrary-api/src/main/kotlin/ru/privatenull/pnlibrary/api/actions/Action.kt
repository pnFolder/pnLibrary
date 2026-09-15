package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.config.ConfigPolymorphic
import ru.privatenull.pnlibrary.api.config.ConfigType
import ru.privatenull.pnlibrary.api.config.ConfigTypes

@ConfigPolymorphic(discriminator = "type")
@ConfigTypes(
    ConfigType(MessageAction::class, "message", aliases = ["messages", "msg"]),
    ConfigType(ActionBarAction::class, "action-bar", aliases = ["actionbar"]),
    ConfigType(SoundAction::class, "sound"),
    ConfigType(ConsoleLogAction::class, "console", aliases = ["log"]),
    ConfigType(DelayAction::class, "delay", aliases = ["later"]),
    ConfigType(ConditionalAction::class, "when", aliases = ["condition", "if"]),
    ConfigType(EffectAction::class, "effect", aliases = ["potion-effect"]),
    ConfigType(ParticleAction::class, "particle", aliases = ["particles"]),
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
