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
    ConfigType(EffectAction::class, "effect", aliases = ["potion-effect"]),
    ConfigType(ParticleAction::class, "particle", aliases = ["particles"]),
)
fun interface Action {
    fun execute(context: ActionContext)
}
