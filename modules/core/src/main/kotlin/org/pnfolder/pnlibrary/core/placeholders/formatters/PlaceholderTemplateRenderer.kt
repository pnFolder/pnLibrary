package org.pnfolder.pnlibrary.core.placeholders.formatters

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Renders conditional blocks and placeholder expressions inside one text template. */
class PlaceholderTemplateRenderer(
    private val resolve: (String, UUID?, Map<String, Any?>) -> CompletionStage<Any?>,
    private val format: (Any?, List<String>, UUID?, Map<String, Any?>) -> Any?,
) {
    /** Fills the conditionals and `{name}` expressions of [template] with [values] and resolved placeholders. */
    fun render(
        template: String,
        playerId: UUID?,
        values: Map<String, Any?>,
    ): CompletionStage<String> =
        renderConditions(template, playerId, values, MAXIMUM_CONDITIONAL_DEPTH)
            .thenCompose { output -> renderOrdinaryExpressions(output, playerId, values) }

    private fun renderOrdinaryExpressions(
        template: String,
        playerId: UUID?,
        values: Map<String, Any?>,
    ): CompletionStage<String> {
        val expressions = ORDINARY_EXPRESSION.findAll(template)
            .map { match -> match.groupValues[1] }
            .distinct()
            .toList()
        var result: CompletionStage<String> = CompletableFuture.completedFuture(template)
        expressions.forEach { expression ->
            result = result.thenCompose { current ->
                resolve(expression, playerId, values).thenApply { value ->
                    current.replace("{$expression}", value?.toString() ?: "{$expression}")
                }
            }
        }
        return result
    }

    private fun renderConditions(
        template: String,
        playerId: UUID?,
        values: Map<String, Any?>,
        depth: Int,
    ): CompletionStage<String> {
        val match = CONDITIONAL.find(template)
        if (match == null || depth == 0) return CompletableFuture.completedFuture(template)
        return resolve(match.groupValues[1], playerId, values).thenCompose { value ->
            val replacement = if (isTruthy(value)) match.groupValues[2] else match.groupValues[3]
            renderConditions(template.replaceRange(match.range, replacement), playerId, values, depth - 1)
        }
    }

    private fun isTruthy(value: Any?): Boolean =
        value != null && value != false && value != 0 && value.toString().isNotBlank()

    private companion object {
        const val MAXIMUM_CONDITIONAL_DEPTH = 16
        val ORDINARY_EXPRESSION = Regex("\\{([^{}]+)}")
        val CONDITIONAL = Regex("\\{\\?([^{}]+)}([\\s\\S]*?)(?:\\{:}([\\s\\S]*?))?\\{/}")
    }
}
