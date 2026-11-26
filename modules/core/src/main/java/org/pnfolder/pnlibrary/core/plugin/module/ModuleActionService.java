package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.registry.Visibility;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionService;
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionConflict;
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionScope;
import org.pnfolder.pnlibrary.api.scenario.actions.ScenarioLookup;
import org.pnfolder.pnlibrary.api.scenario.expression.Expression;
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;
import org.pnfolder.pnlibrary.core.registry.ListRegistry;

/**
 * Action service of one module: every context gets the module's text, logger, tasks, placeholders and services,
 * and sees the names it may see, the nearest first: its own module, its own plugin, the others.
 */
public final class ModuleActionService implements ActionService {
    private final ModuleResources resources;
    private final ScenarioDefinitions shared;
    private final String owner;
    private final ScenarioLookup lookup;

    public ModuleActionService(ModuleResources resources, ScenarioDefinitions shared, String owner) {
        this.resources = resources;
        this.shared = shared;
        this.owner = owner;
        this.lookup = shared.view(owner);
    }

    @Override
    public @NotNull ActionContext context(@NotNull OnlinePlayer player, @NotNull Audience allPlayers,
                                          @NotNull Map<String, Object> values, @NotNull ComponentSerializerType serializerType) {
        return new ActionContext(player, allPlayers, resources.getComponents(), resources.getLogger(), resources.getTasks(),
                serializerType, values, Collections.<Class<?>, Object>emptyMap(), new Function<Class<?>, Object>() {
                    @Override
                    public Object apply(Class<?> type) {
                        return resources.getServices().get(type);
                    }
                }, lookup, resources.getPlaceholders());
    }

    @Override
    public @NotNull Registry<Action> actions() {
        return new ListRegistry<Action>("actions", () -> shared.actionEntries(owner));
    }

    @Override
    public @NotNull Registry<Requirement> requirements() {
        return new ListRegistry<Requirement>("requirements", () -> shared.requirementEntries(owner));
    }

    @Override
    public @NotNull Registry<ExpressionFunction> functions() {
        return new ListRegistry<ExpressionFunction>("functions", () -> shared.functionEntries(owner));
    }

    @Override
    public @NotNull AutoCloseable define(@NotNull String name, @NotNull Action action, @NotNull DefinitionScope scope,
                                         @NotNull DefinitionConflict conflict) {
        return shared.defineAction(name, action, owner, conflict, visibility(scope), warner());
    }

    @Override
    public @NotNull AutoCloseable defineRequirement(@NotNull String name, @NotNull Requirement requirement,
                                                    @NotNull DefinitionScope scope, @NotNull DefinitionConflict conflict) {
        return shared.defineRequirement(name, requirement, owner, conflict, visibility(scope), warner());
    }

    @Override
    public @NotNull AutoCloseable defineFunction(@NotNull String name, @NotNull ExpressionFunction function,
                                                 @NotNull DefinitionScope scope, @NotNull DefinitionConflict conflict) {
        if (Expression.isBuiltin(name.trim().toLowerCase())) {
            throw new IllegalArgumentException("'" + name + "' is a function of the expression language itself");
        }
        return shared.defineFunction(name, function, owner, conflict, visibility(scope), warner());
    }

    private Consumer<String> warner() {
        return message -> resources.getLogger().warning(message);
    }

    private static Visibility visibility(DefinitionScope scope) {
        switch (scope) {
            case MODULE:
                return Visibility.MODULE;
            case PLUGIN:
                return Visibility.PLUGIN;
            default:
                return Visibility.ALL;
        }
    }
}
