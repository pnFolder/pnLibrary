package org.pnfolder.pnlibrary.api.scenario.expression;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;

/**
 * A small, safe calculator for conditions and numbers in configuration. It cannot touch files or run code.
 *
 * <pre>
 * level &gt;= 10 &amp;&amp; (has_vip || chance(0.25))
 * clamp(balance / 100, 1, 10) * 5
 * contains(upper(rank), 'ADMIN') ? 1 : 0
 * </pre>
 *
 * <p><b>Values.</b> A bare name reads a value of the run, then a pnLibrary placeholder. {@code {plugin:key}} reads a
 * pnLibrary placeholder, {@code %name%} an external one, {@code [name]} a value of the run (use it for names with
 * dashes). Text that looks like a number counts as a number.
 *
 * <p><b>Operators</b>, tightest first: {@code !} and unary {@code -}; {@code ^} (power); {@code * / %};
 * {@code + -} ({@code +} joins text when a side is not a number); {@code < <= > >=}; {@code == !=}; {@code &&};
 * {@code ||}; {@code condition ? a : b}. Numbers, {@code 'text'}, {@code "text"}, {@code true}, {@code false}.
 *
 * <p><b>Functions:</b> {@code min}, {@code max}, {@code abs}, {@code round(x, digits)}, {@code floor}, {@code ceil},
 * {@code sqrt}, {@code pow}, {@code clamp(x, low, high)}, {@code random(low, high)}, {@code chance(p)}, {@code len},
 * {@code upper}, {@code lower}, {@code contains}, {@code startsWith}, {@code endsWith}, {@code has(name)},
 * {@code if(c, a, b)}, and about the player {@code permission(node)}, {@code level()}, {@code exp()},
 * {@code items(material)}, {@code distance(world, x, y, z)}. Plugins add more with
 * {@code ActionService.defineFunction}.
 *
 * <pre>{@code
 * Expression expression = Expression.parse("level() >= 10 && permission('shop.use')");
 * boolean allowed = expression.test(context);
 * }</pre>
 */
public final class Expression {
    /** One evaluated part of the parsed text. */
    interface Node {
        @Nullable
        Object eval(RequirementContext context);
    }

    private final String source;
    private final Node root;

    private Expression(String source, Node root) {
        this.source = source;
        this.root = root;
    }

    /** Parses {@code text}; throws {@link ExpressionException} with the position of the mistake. */
    @NotNull
    public static Expression parse(@NotNull String text) {
        return new Expression(text, new Parser(text).parseAll());
    }

    /** Writes {@code value} as text: {@code 5.0} becomes {@code 5}, {@code null} becomes an empty text. */
    @NotNull
    public static String format(@Nullable Object value) {
        return ExpressionValues.format(value);
    }

    /** Whether {@code name} is a function of the language itself, which plugins cannot redefine. */
    public static boolean isBuiltin(@NotNull String name) {
        return ExpressionFunctions.contains(name);
    }

    /** The text this expression was parsed from. */
    @NotNull
    public String getSource() {
        return source;
    }

    /** Computes the value: {@code Double}, {@code Boolean}, {@code String}, or {@code null} for an unknown name. */
    @Nullable
    public Object evaluate(@NotNull RequirementContext context) {
        return root.eval(context);
    }

    /** Computes the value and reads it as a yes/no answer. */
    public boolean test(@NotNull RequirementContext context) {
        return ExpressionValues.truthy(evaluate(context));
    }

    /** Computes the value as text, with whole numbers written without a decimal point. */
    @NotNull
    public String text(@NotNull RequirementContext context) {
        return format(evaluate(context));
    }

    @NotNull
    @Override
    public String toString() {
        return source;
    }
}
