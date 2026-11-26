package org.pnfolder.pnlibrary.api.scenario.actions;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.api.text.ComponentService;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One run of a scenario: who it is for, the values it knows, the services it may use, and what to undo.
 *
 * <p>Create it with {@link ActionService#context}, run a list with {@link #execute}, and read the
 * {@link ActionResult}. A context belongs to one run and is not thread-safe: do not share it between concurrent runs.
 *
 * <pre>{@code
 * ActionContext context = module.getActions().context(player);
 * ActionResult result = context.execute(actions);
 * if (result instanceof ActionResult.Failed) {
 *     player.sendMessage(Component.text(((ActionResult.Failed) result).getMessage()));
 * }
 * }</pre>
 *
 * <p>Every text an action prints may mention values, in three kinds of marks, see {@link #format}.
 */
public final class ActionContext implements RequirementContext {
    private final OnlinePlayer player;
    private final Audience allPlayers;
    private final ComponentService components;
    private final PnLogger logger;
    private final TaskScope tasks;
    private final ComponentSerializerType serializerType;
    private final ScenarioValues values;
    private final ScenarioText texts;
    private final Map<Class<?>, Object> services;
    private final Function<Class<?>, Object> serviceLookup;
    private final ScenarioLookup lookup;
    private final PlaceholderService placeholders;
    private final UndoLog undoLog = new UndoLog();

    /** Creates a context with the adaptive text format, no values and no extra services. */
    public ActionContext(@NotNull OnlinePlayer player, @NotNull Audience allPlayers, @NotNull ComponentService components,
                         @NotNull PnLogger logger, @NotNull TaskScope tasks) {
        this(player, allPlayers, components, logger, tasks, ComponentSerializerType.ADAPTIVE,
                Collections.<String, Object>emptyMap(), Collections.<Class<?>, Object>emptyMap(), type -> null,
                ScenarioLookup.NONE, null);
    }

    /**
     * Creates a context.
     *
     * @param player player the run is for
     * @param allPlayers wider audience for actions aimed at {@link ActionTarget#ALL_PLAYERS}
     * @param components parser used by actions that print text
     * @param logger plugin logger for warnings
     * @param tasks task scope for delayed work
     * @param serializerType default text format of printed text
     * @param values named values texts and requirements read as {@code {name}}
     * @param services services handed over directly, by exact type
     * @param serviceLookup fallback for services registered elsewhere, such as in the module's service scope
     * @param lookup actions, requirements and functions plugins defined by name
     * @param placeholders placeholder service, or {@code null} when the runtime has none
     */
    public ActionContext(@NotNull OnlinePlayer player, @NotNull Audience allPlayers, @NotNull ComponentService components,
                         @NotNull PnLogger logger, @NotNull TaskScope tasks, @NotNull ComponentSerializerType serializerType,
                         @NotNull Map<String, Object> values, @NotNull Map<Class<?>, Object> services,
                         @NotNull Function<Class<?>, Object> serviceLookup, @NotNull ScenarioLookup lookup,
                         @Nullable PlaceholderService placeholders) {
        this.player = player;
        this.allPlayers = allPlayers;
        this.components = components;
        this.logger = logger;
        this.tasks = tasks;
        this.serializerType = serializerType;
        this.values = new ScenarioValues(values);
        this.services = services;
        this.serviceLookup = serviceLookup;
        this.lookup = lookup;
        this.placeholders = placeholders;
        this.texts = new ScenarioText(this, player, logger, placeholders, this.values.live());
    }

    /** The player the run is for. */
    @NotNull
    @Override
    public OnlinePlayer getPlayer() {
        return player;
    }

    /** Wider audience for actions aimed at {@link ActionTarget#ALL_PLAYERS}. */
    @NotNull
    public Audience getAllPlayers() {
        return allPlayers;
    }

    /** Parser used by actions that print text. */
    @NotNull
    public ComponentService getComponents() {
        return components;
    }

    @NotNull
    @Override
    public PnLogger getLogger() {
        return logger;
    }

    /** Task scope for delayed work. */
    @NotNull
    public TaskScope getTasks() {
        return tasks;
    }

    /** Default text format of printed text. */
    @NotNull
    public ComponentSerializerType getSerializerType() {
        return serializerType;
    }

    /** Placeholder service, or {@code null} when the runtime has none. */
    @Nullable
    public PlaceholderService getPlaceholders() {
        return placeholders;
    }

    /**
     * Runs {@code actions} as one scenario: in order, until one stops or fails.
     *
     * <p>A failure undoes the steps that registered an {@link #onUndo} and writes a warning; a thrown exception
     * counts as a failure with the code {@code action.error}. Use this for the outermost list; actions that
     * contain lists use {@link #runList}, so that the undo covers the whole scenario.
     */
    @NotNull
    public ActionResult execute(@NotNull List<Action> actions) {
        ActionResult result = runList(actions);
        if (result instanceof ActionResult.Failed) {
            ActionResult.Failed failed = (ActionResult.Failed) result;
            logger.warning("Scenario failed (" + failed.getCode() + "): " + failed.getMessage(), failed.getCause());
            rollbackTo(0);
        }
        return result;
    }

    /**
     * Runs {@code actions} in order and returns the first result that is not {@link ActionResult.Done}, without
     * undoing. It is the building block for actions that hold other actions.
     */
    @NotNull
    public ActionResult runList(@NotNull List<Action> actions) {
        for (Action action : actions) {
            ActionResult result;
            try {
                result = action.run(this);
            } catch (Exception error) {
                String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
                result = new ActionResult.Failed("action.error", message, error);
            }
            if (!(result instanceof ActionResult.Done)) {
                return result;
            }
        }
        return ActionResult.Done.INSTANCE;
    }

    /** Runs {@code actions} as a separate scenario after {@code delay}, without blocking the caller. */
    public void runLater(@NotNull Duration delay, @NotNull List<Action> actions) {
        if (actions.isEmpty()) {
            return;
        }
        tasks.schedule(TaskSpec.builder()
                .execution(TaskExecution.global())
                .delay(delay)
                .action(task -> execute(actions))
                .build());
    }

    /**
     * Registers how to take back what the current step did, for example giving a charge back.
     *
     * <p>When a later step fails, registered undo steps run in reverse order. {@code description} is for the log.
     */
    public void onUndo(@NotNull String description, @NotNull Runnable undo) {
        undoLog.add(description, undo);
    }

    /** A bookmark of the undo list, for {@link #rollbackTo}. */
    public int undoMark() {
        return undoLog.size();
    }

    /** Forgets the undo steps registered after {@code mark} without running them: what they did stays. */
    public void discardUndo(int mark) {
        undoLog.discardAfter(mark);
    }

    /** Runs and removes the undo steps registered after {@code mark}, newest first. One that throws is logged. */
    public void rollbackTo(int mark) {
        undoLog.rollbackAfter(mark, logger);
    }

    @Nullable
    @Override
    public <T> T service(@NotNull Class<T> type) {
        Object found = services.get(type);
        if (found == null) {
            found = serviceLookup.apply(type);
        }
        return found != null ? type.cast(found) : null;
    }

    /** Returns the service of {@code type}, or throws when this run has none. */
    @NotNull
    public <T> T requireService(@NotNull Class<T> type) {
        T service = service(type);
        if (service == null) {
            throw new IllegalStateException(type.getSimpleName() + " is not available to this scenario");
        }
        return service;
    }

    @Nullable
    @Override
    public Object value(@NotNull String name) {
        return values.live().get(name);
    }

    @Nullable
    @Override
    public Requirement requirement(@NotNull String name) {
        return lookup.requirement(name);
    }

    @Nullable
    @Override
    public ExpressionFunction function(@NotNull String name) {
        return lookup.function(name);
    }

    /** The action defined under {@code name} with {@link ActionService#define}, or {@code null}. */
    @Nullable
    public Action action(@NotNull String name) {
        return lookup.action(name);
    }

    /**
     * Runs {@code block} with {@code extra} values added, then puts the values back as they were. Use it to give a
     * called action its arguments without leaking them into the rest of the scenario.
     */
    public <T> T scoped(@NotNull Map<String, Object> extra, @NotNull Supplier<T> block) {
        return values.scoped(extra, block);
    }

    /** Makes {@code value} available as {@code [name]} to later steps. */
    @NotNull
    public ActionContext setValue(@NotNull String name, @Nullable Object value) {
        values.put(name, value);
        return this;
    }

    /** A snapshot of the values known now. */
    @NotNull
    public Map<String, Object> values() {
        return values.snapshot();
    }

    /** The audience an action aimed at {@code target} sends to. */
    @NotNull
    public Audience target(@NotNull ActionTarget target) {
        return target == ActionTarget.PLAYER ? player : allPlayers;
    }

    /** Fills in the placeholders of {@code text} (see {@link #format}), then parses it with the default format. */
    @NotNull
    public Component component(@NotNull String text) {
        return component(text, null);
    }

    /** Fills in the placeholders of {@code text} (see {@link #format}), then parses it with {@code type} or the default format. */
    @NotNull
    public Component component(@NotNull String text, @Nullable ComponentSerializerType type) {
        return components.deserialize(format(text), type != null ? type : serializerType);
    }

    /** Parses {@code lines} as one component, one line under another, with the default format. */
    @NotNull
    public Component component(@NotNull Iterable<String> lines) {
        return component(lines, null);
    }

    /** Parses {@code lines} as one component, one line under another. */
    @NotNull
    public Component component(@NotNull Iterable<String> lines, @Nullable ComponentSerializerType type) {
        Component result = null;
        for (String line : lines) {
            Component next = component(line, type);
            result = result == null ? next : result.append(Component.newline()).append(next);
        }
        return result != null ? result : Component.empty();
    }

    /**
     * Replaces the placeholders in plain {@code text}, each kind in its own brackets, in one pass:
     *
     * <table summary="placeholder kinds">
     * <tr><td>{@code {name}}</td><td>a value of this run (and {@code player}), else a pnLibrary placeholder</td></tr>
     * <tr><td>{@code {name}}, {@code {plugin:key}}</td><td>pnLibrary placeholders: the plugin's own, the library's, another plugin's</td></tr>
     * <tr><td>{@code %name%}</td><td>the external PlaceholderAPI only</td></tr>
     * </table>
     *
     * <p>{@code {name}} takes a chain of formats after a bar, such as {@code {money|group}}. {@code player} and
     * {@code player_name} are always the player's name. Text that a placeholder produced is not read again. Unknown
     * names stay as written, so a typo is visible instead of silently empty.
     */
    @NotNull
    public String format(@NotNull String text) {
        return texts.format(text);
    }

    @NotNull
    @Override
    public String text(@NotNull String expression) {
        return texts.text(expression);
    }

    @Nullable
    @Override
    public Object read(@NotNull String expression) {
        return texts.read(expression);
    }

    /** Resolves a bare {@code name} or {@code {name|formats}} as a run value first, then as a pnLibrary placeholder. */
    @Nullable
    public Object resolve(@NotNull String reference) {
        return texts.resolve(reference);
    }
}
