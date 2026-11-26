package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Runs an action a plugin defined by name (see {@code ActionService.define}), giving it {@code args} as values for
 * the call. The arguments are visible inside the call only. Calls may nest to {@link #MAX_DEPTH} levels.
 *
 * <pre>{@code
 * - { type: call, name: give-kit, args: { kit: starter, amount: '{= 2 * level()}' } }
 * }</pre>
 */
public final class CallAction implements Action {
    /** The deepest chain of calls inside calls. */
    public static final int MAX_DEPTH = 16;
    private static final String DEPTH = "_call_depth";

    private final String name;
    private final Map<String, String> args;

    /** Creates the action with every setting at its default. */
    public CallAction() {
        this("", Collections.<String, String>emptyMap());
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public CallAction(@NotNull String name) {
        this(name, Collections.<String, String>emptyMap());
    }

    /** Creates it with every setting given. */
    public CallAction(@NotNull String name, @NotNull Map<String, String> args) {
        this.name = name;
        this.args = args;
    }

    /** The defined name. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Values for the call; placeholders in them are filled in first. */
    @NotNull
    public Map<String, String> getArgs() {
        return args;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        Action target = context.action(name);
        if (target == null) {
            return new ActionResult.Failed("call.unknown", "Action '" + name + "' is not defined");
        }
        Object current = context.value(DEPTH);
        int depth = current instanceof Number ? ((Number) current).intValue() : 0;
        if (depth >= MAX_DEPTH) {
            return new ActionResult.Failed("call.too-deep", "Action '" + name + "' is called too deeply; check for a loop");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : args.entrySet()) {
            values.put(entry.getKey(), context.format(entry.getValue()));
        }
        values.put(DEPTH, depth + 1);
        return context.scoped(values, () -> target.run(context));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CallAction)) {
            return false;
        }
        CallAction that = (CallAction) other;
        return Objects.equals(name, that.name)
                && Objects.equals(args, that.args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, args);
    }

    @NotNull
    @Override
    public String toString() {
        return "CallAction(name=" + name + ", args=" + args + ")";
    }
}
