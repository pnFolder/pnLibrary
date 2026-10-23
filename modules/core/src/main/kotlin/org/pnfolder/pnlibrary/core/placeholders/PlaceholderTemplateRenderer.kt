package org.pnfolder.pnlibrary.core.placeholders

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Renders conditional blocks and placeholder expressions inside one text template. */
internal class PlaceholderTemplateRenderer(
    private val resolve: (String, UUID?, Map<String, Any?>) -> CompletionStage<Any?>,
    private val format: (Any?, List<String>, UUID?, Map<String, Any?>) -> Any?,
) {
    fun render(
        template: String,
        playerId: UUID?,
        values: Map<String, Any?>,
    ): CompletionStage<String> =
        renderConditions(template, playerId, values, MAXIMUM_CONDITIONAL_DEPTH)
            .thenCompose { output -> renderLocalExpressions(output, playerId, values) }
            .thenCompose { output -> renderOrdinaryExpressions(output, playerId, values) }

    private fun renderLocalExpressions(
        template: String,
        playerId: UUID?,
        values: Map<String, Any?>,
    ): CompletionStage<String> {
        val expressions = LOCAL_EXPRESSION.findAll(template)
            .map { match -> match.value }
            .distinct()
            .mapNotNull { token -> LocalPlaceholderExpression.parse(token)?.let { token to it } }
            .toList()
        var result: CompletionStage<String> = CompletableFuture.completedFuture(template)
        expressions.forEach { (token, expression) ->
            result = result.thenCompose { current ->
                resolve(expression.reference, playerId, values).thenApply { value ->
                    val formatted = format(value, expression.formatters, playerId, values)
                    current.replace(token, formatted?.toString() ?: token)
                }
            }
        }
        return result
    }

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
        val LOCAL_EXPRESSION = Regex("\\[[^\\[\\]]+]")
        val ORDINARY_EXPRESSION = Regex("\\{([^{}]+)}")
        val CONDITIONAL = Regex("\\{\\?([^{}]+)}([\\s\\S]*?)(?:\\{:}([\\s\\S]*?))?\\{/}")
    }
}
