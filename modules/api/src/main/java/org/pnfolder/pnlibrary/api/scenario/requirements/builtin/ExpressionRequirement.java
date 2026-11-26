package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.expression.Expression;
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionException;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.util.Objects;

/**
 * Met when a calculation comes out true. This is the way to write anything the other types cannot: maths, the
 * player's level, placeholders, and any mix of them. See {@link Expression} for the whole language.
 *
 * <pre>{@code
 * type: expression
 * expression: "level() >= 10 && (permission('shop.vip') || {shop:discount} > 20)"
 * }</pre>
 *
 * <p>A mistake in the text is reported when the configuration loads. A calculation that fails at run time (for
 * example a text where a number is needed) is not met, and the reason says why.
 *
 * @throws ExpressionException when the text cannot be parsed
 */
public final class ExpressionRequirement implements Requirement {
    private final String expression;
    private transient volatile Expression parsed;
    private transient volatile String parsedSource;

    /** Creates it with every setting at its default. */
    public ExpressionRequirement() {
        this("true");
    }

    /** Creates it with every setting given. */
    public ExpressionRequirement(@NotNull String expression) {
        this.expression = Objects.requireNonNull(expression, "expression");
        parsed();
    }

    /** The calculation. */
    @NotNull
    public String getExpression() {
        return expression;
    }

    private Expression parsed() {
        Expression current = parsed;
        if (current == null || !expression.equals(parsedSource)) {
            current = Expression.parse(expression);
            parsed = current;
            parsedSource = expression;
        }
        return current;
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        try {
            return Verdict.of(parsed().test(context), "'" + expression + "' is false");
        } catch (ExpressionException error) {
            context.getLogger().warning("Expression '" + expression + "' failed: " + error.getMessage());
            return new Verdict.Unmet("'" + expression + "' could not be calculated: " + error.getMessage());
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExpressionRequirement)) {
            return false;
        }
        ExpressionRequirement that = (ExpressionRequirement) other;
        return Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }

    @NotNull
    @Override
    public String toString() {
        return "ExpressionRequirement(expression=" + expression + ")";
    }
}
