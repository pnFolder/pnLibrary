package org.pnfolder.pnlibrary.api.scenario.actions;

import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Creates the contexts of one module's scenarios, and keeps the actions, requirements and functions the plugin
 * defined by name.
 *
 * <p>It holds no scenarios itself: the plugin keeps its parsed {@code List<Action>} and starts it for a player.
 *
 * <pre>{@code
 * module.getActions().define("give-kit", ctx -> {      // later: - { type: call, name: give-kit }
 *     kits.give(ctx.getPlayer(), ctx.format("[kit]"));
 *     return ActionResult.Done.INSTANCE;
 * });
 * module.getActions().defineRequirement("is-vip", ctx -> Verdict.of(vip(ctx.getPlayer()), "VIP only"));
 * module.getActions().defineFunction("kills", (ctx, args) -> stats.kills(ctx.getPlayer().getUniqueId()));
 *
 * ActionResult result = module.getActions().execute(config.onBuy, player, player, values);
 * }</pre>
 */
@ApiStatus.NonExtendable
public interface ActionService {
    /**
     * Creates a fresh context for one run.
     *
     * @param player player the run is for
     * @param allPlayers audience for actions aimed at everyone
     * @param values named values texts and requirements can read as {@code [name]}
     * @param serializerType default format of printed text
     */
    @NotNull
    ActionContext context(@NotNull OnlinePlayer player, @NotNull Audience allPlayers, @NotNull Map<String, Object> values,
                          @NotNull ComponentSerializerType serializerType);

    /** Creates a context for {@code player} alone, with no values and the adaptive text format. */
    @NotNull
    default ActionContext context(@NotNull OnlinePlayer player) {
        return context(player, player, Collections.<String, Object>emptyMap());
    }

    /** Creates a context with the adaptive text format. */
    @NotNull
    default ActionContext context(@NotNull OnlinePlayer player, @NotNull Audience allPlayers, @NotNull Map<String, Object> values) {
        return context(player, allPlayers, values, ComponentSerializerType.ADAPTIVE);
    }

    /** Creates a context and runs {@code actions} as one scenario; see {@link ActionContext#execute}. */
    @NotNull
    default ActionResult execute(@NotNull List<Action> actions, @NotNull OnlinePlayer player, @NotNull Audience allPlayers,
                                 @NotNull Map<String, Object> values) {
        return context(player, allPlayers, values).execute(actions);
    }

    /** Runs {@code actions} for {@code player} alone, with no values. */
    @NotNull
    default ActionResult execute(@NotNull List<Action> actions, @NotNull OnlinePlayer player) {
        return execute(actions, player, player, Collections.<String, Object>emptyMap());
    }

    /**
     * Gives {@code action} a {@code name} so that scenarios can run it with {@code type: call}, passing {@code args}
     * as values. Names are lowercase letters, digits, dots, hyphens and underscores.
     *
     * @param scope how far the name reaches
     * @param conflict what to do when the name is taken
     * @return a handle that removes the definition when closed
     */
    @NotNull
    AutoCloseable define(@NotNull String name, @NotNull Action action, @NotNull DefinitionScope scope, @NotNull DefinitionConflict conflict);

    /** Defines {@code action} for this module only; a name that is taken fails. */
    @NotNull
    default AutoCloseable define(@NotNull String name, @NotNull Action action) {
        return define(name, action, DefinitionScope.MODULE, DefinitionConflict.FAIL);
    }

    /** Gives {@code requirement} a {@code name} for {@code type: ref} and for {@code RequirementContext.requirement}. */
    @NotNull
    AutoCloseable defineRequirement(@NotNull String name, @NotNull Requirement requirement, @NotNull DefinitionScope scope,
                                    @NotNull DefinitionConflict conflict);

    /** Defines {@code requirement} for this module only; a name that is taken fails. */
    @NotNull
    default AutoCloseable defineRequirement(@NotNull String name, @NotNull Requirement requirement) {
        return defineRequirement(name, requirement, DefinitionScope.MODULE, DefinitionConflict.FAIL);
    }

    /** Adds {@code function} to the expression language under {@code name}, for example {@code kills()}. Built-in names cannot be taken. */
    @NotNull
    AutoCloseable defineFunction(@NotNull String name, @NotNull ExpressionFunction function, @NotNull DefinitionScope scope,
                                 @NotNull DefinitionConflict conflict);

    /** Defines {@code function} for this module only; a name that is taken fails. */
    @NotNull
    default AutoCloseable defineFunction(@NotNull String name, @NotNull ExpressionFunction function) {
        return defineFunction(name, function, DefinitionScope.MODULE, DefinitionConflict.FAIL);
    }

    /** Every named action this module can see, with owner and visibility; see {@link #define}. */
    @NotNull
    Registry<Action> actions();

    /** Every named requirement this module can see; see {@link #defineRequirement}. */
    @NotNull
    Registry<Requirement> requirements();

    /** Every named expression function this module can see; see {@link #defineFunction}. */
    @NotNull
    Registry<ExpressionFunction> functions();
}
