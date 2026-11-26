package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.scenario.expression.Expression;

import java.util.Objects;

/**
 * Stores a value for the steps after it, readable as {@code [name]}. Give either a {@code value} (text with
 * placeholders) or an {@code expression} (a calculation, see {@link Expression}).
 *
 * <pre>{@code
 * - { type: set, name: price, expression: "round(base * (1 - {shop:discount} / 100), 2)" }
 * - { type: message, messages: ['Price: [price]'] }
 * }</pre>
 *
 * <p>Reading it fails with {@code ExpressionException} when the expression cannot be parsed
 */
public final class SetAction implements Action {
    private final String name;
    private final String value;
    private final String expression;
    private transient volatile Expression parsed;
    private transient volatile String parsedSource;

    /** Creates the action with every setting at its default. */
    public SetAction() {
        this("", "", null);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public SetAction(@NotNull String name) {
        this(name, "", null);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public SetAction(@NotNull String name, @NotNull String value) {
        this(name, value, null);
    }

    /** Creates it with every setting given. */
    public SetAction(@NotNull String name, @NotNull String value, @Nullable String expression) {
        this.name = Objects.requireNonNull(name, "name");
        this.value = value;
        this.expression = expression;
        parse();
    }

    /** The value's name. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Text to store, with placeholders filled in. */
    @NotNull
    public String getValue() {
        return value;
    }

    /** Calculation to store; wins over {@code value}. */
    @Nullable
    public String getExpression() {
        return expression;
    }

    private Expression parse() {
        String source = expression;
        if (source == null) {
            return null;
        }
        Expression current = parsed;
        if (current == null || !source.equals(parsedSource)) {
            current = Expression.parse(source);
            parsed = current;
            parsedSource = source;
        }
        return current;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        if (name.trim().isEmpty()) {
            return new ActionResult.Failed("set.no-name", "A set action needs a name");
        }
        Expression calculation = parse();
        Object result = calculation != null ? calculation.evaluate(context) : null;
        context.setValue(name, result != null ? result : context.format(value));
        return ActionResult.Done.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SetAction)) {
            return false;
        }
        SetAction that = (SetAction) other;
        return Objects.equals(name, that.name)
                && Objects.equals(value, that.value)
                && Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, expression);
    }

    @NotNull
    @Override
    public String toString() {
        return "SetAction(name=" + name + ", value=" + value + ", expression=" + expression + ")";
    }
}
