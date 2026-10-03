package ru.privatenull.pnlibrary.core.placeholders

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
    ): CompletionStage<String> {
        var output = renderConditions(template, playerId, values)
        output = renderLocalExpressions(output, playerId, values)
        return renderOrdinaryExpressions(output, playerId, values)
    }

    private fun renderLocalExpressions(
        template: String,
        playerId: UUID?,
        values: Map<String, Any?>,
    ): String {
        var output = template
        val tokens = LOCAL_EXPRESSION.findAll(output)
            .map { match -> match.value }
            .filter { token -> LocalPlaceholderExpression.parse(token) != null }
            .distinct()
            .toList()

        tokens.forEach { token ->
            val expression = LocalPlaceholderExpression.parse(token) ?: return@forEach
            val value = resolve(expression.reference, playerId, values).toCompletableFuture().join()
            val formatted = format(value, expression.formatters, playerId, values)
            output = output.replace(token, formatted?.toString() ?: token)
        }
        return output
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
    ): String {
        var output = template
        repeat(MAXIMUM_CONDITIONAL_DEPTH) {
            val match = CONDITIONAL.find(output) ?: return output
            val value = resolve(match.groupValues[1], playerId, values).toCompletableFuture().join()
            val replacement = if (isTruthy(value)) match.groupValues[2] else match.groupValues[3]
            output = output.replaceRange(match.range, replacement)
        }
        return output
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
