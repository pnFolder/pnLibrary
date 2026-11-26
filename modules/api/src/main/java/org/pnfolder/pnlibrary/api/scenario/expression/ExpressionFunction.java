package org.pnfolder.pnlibrary.api.scenario.expression;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;

import java.util.List;

/**
 * A function that expressions may call, such as {@code min(a, b)} or one a plugin adds with
 * {@code ActionService.defineFunction}.
 *
 * <pre>{@code
 * module.getActions().defineFunction("kills", (ctx, args) -> stats.kills(ctx.getPlayer().getUniqueId()));
 * // later, in a configuration:  when: { type: expression, expression: 'kills() >= 100' }
 * }</pre>
 */
@FunctionalInterface
public interface ExpressionFunction {
    /** Computes the result from {@code arguments}: numbers as {@code Double}, booleans, or text. */
    @Nullable
    Object call(@NotNull RequirementContext context, @NotNull List<Object> arguments);
}
