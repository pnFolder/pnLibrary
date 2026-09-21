package ru.privatenull.pnlibrary.demo

import ru.privatenull.pnlibrary.api.actions.*
import ru.privatenull.pnlibrary.api.actions.standard.check.Expansion
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import org.bukkit.entity.Player
import java.time.Duration

/**
 * Exhaustive catalogue of the public action implementations.  It is deliberately explicit:
 * adding a new action requires adding it here, so the demo becomes a compile-time API checklist.
 */
object DemoActionShowcase {
    fun run(plugin: DemoPlugin, nativePlayer: Player, values: Map<String, Any?> = emptyMap()) {
        val player = PnLibraryProvider.get().audiences.player(nativePlayer.uniqueId) ?: return
        val all = PnLibraryProvider.get().audiences.all()
        val context = plugin.context.actions.context(player, all, values + ("demo" to true))
        plugin.context.actions.execute(context, executableActions())
        plugin.logger.info("action showcase executed: ${executableActions().size} action types")
        catalogModels()
    }

    private fun executableActions(): List<Action> = listOf(
        MessageAction(listOf("<green>MessageAction")),
        ActionBarAction("<yellow>ActionBarAction"),
        SoundAction(),
        ParticleAction(count = 0),
        EffectAction(duration = Duration.ofSeconds(1)),
        ConsoleLogAction("ConsoleLogAction"),
        NoOpAction(),
        SetValueAction("branch", "yes"),
        CopyValueAction("branch", "branch-copy"),
        ConditionalAction(
            allChecks = listOf(EnabledCondition("demo"), ValueCondition("branch", Comparison.EQUALS, "yes")),
            thenActions = listOf(MessageAction(listOf("<aqua>ConditionalAction / all"))),
            elseActions = listOf(MessageAction(listOf("<red>ConditionalAction / else"))),
        ),
        SwitchAction("branch", mapOf("yes" to listOf(MessageAction(listOf("<aqua>SwitchAction / case")))),
            defaultActions = listOf(NoOpAction())),
        SequenceAction(listOf(MessageAction(listOf("<white>SequenceAction")), NoOpAction())),
        DelayAction(Duration.ZERO, listOf(MessageAction(listOf("<gray>DelayAction")))),
        RemoveValueAction("branch-copy"),
    )

    /** Instantiates non-executable config models and every condition/operator family. */
    private fun catalogModels(): List<Any> = listOf(
        UpdatePlaceholderAction("pndemo:coins", "1", "updated"),
        PlayerEffect("speed", Duration.ofSeconds(1), 0, true, true, true),
        PlayerParticle("flame", 0, 0.0, 0.0, 0.0, 0.0),
        PermissionCondition("pndemo.use"), ChanceCondition(1.0), NotCondition(null),
        ValueCondition("demo", Comparison.PRESENT),
        Expansion(),
        Expansion.If(Expansion.Condition.Check(Expansion.Expression.variable("demo"), Expansion.CheckOperator.IS_NOT_NULL)),
        Expansion.Condition.Comparison(Expansion.Expression.literal(1), Expansion.ComparisonOperator.EQUALS,
            Expansion.Expression.literal(1)),
        Expansion.Condition.All(emptyList()), Expansion.Condition.Any(emptyList()),
        Expansion.Condition.Not(Expansion.Condition.All(emptyList())),
        Expansion.Expression.literal(Expansion.ConfigValue.arrayOf(Expansion.ConfigValue.of("demo"))),
        Expansion.Expression.function(Expansion.FunctionType.TO_STRING, Expansion.Expression.variable("demo")),
        Expansion.ConfigValue.objectOf("demo" to Expansion.ConfigValue.Boolean(true)),
        *Comparison.values(), *Expansion.ComparisonOperator.values(), *Expansion.CheckOperator.values(),
        *Expansion.FunctionType.values(), *ActionTarget.values(),
    )
}
