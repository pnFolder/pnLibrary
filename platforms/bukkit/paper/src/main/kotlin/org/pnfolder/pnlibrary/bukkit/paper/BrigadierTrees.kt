package org.pnfolder.pnlibrary.bukkit.paper

import com.mojang.brigadier.Command
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentSpec
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeKind
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

internal interface NativeInvocation<S> {
    fun execute(source: S, input: String)
    fun suggest(source: S, input: String): CompletionStage<List<String>>
    fun allowed(source: S, node: CommandNode): Boolean = true
}

// Brigadier validates client-side; the portable router still handles all logic after the tree matches.
internal object BrigadierTrees {
    private const val EXTRA_ARGUMENTS = "arguments"

    fun <S> build(definition: CommandDefinition, invocation: NativeInvocation<S>): LiteralCommandNode<S> {
        val root = LiteralArgumentBuilder.literal<S>(definition.name)
            .requires { invocation.allowed(it, definition.root) }
        populate(root, definition.root, invocation)
        return root.build()
    }

    private fun <S> populate(builder: ArgumentBuilder<S, *>, node: CommandNode, invocation: NativeInvocation<S>) {
        if (node.isExecutable) {
            builder.executes { context ->
                invocation.execute(context.source, context.input)
                Command.SINGLE_SUCCESS
            }
        }
        node.children.forEach { builder.then(child(it, invocation)) }
        // Allow free words when no argument child exists — the portable router parses them itself.
        if (node.acceptsExtraArguments &&
            node.isExecutable &&
            node.children.none { it.kind == CommandNodeKind.ARGUMENT }
        ) {
            builder.then(
                RequiredArgumentBuilder.argument<S, Any>(EXTRA_ARGUMENTS, typeOf(ArgumentSpec.GREEDY))
                    .requires { invocation.allowed(it, node) }
                    .suggests(suggestionsFor(invocation))
                    .executes { context ->
                        invocation.execute(context.source, context.input)
                        Command.SINGLE_SUCCESS
                    },
            )
        }
    }

    private fun <S> child(node: CommandNode, invocation: NativeInvocation<S>): ArgumentBuilder<S, *> =
        when (node.kind) {
            CommandNodeKind.LITERAL -> LiteralArgumentBuilder.literal<S>(node.name)
                .requires { invocation.allowed(it, node) }
                .also { populate(it, node, invocation) }
            else -> {
                val spec = node.argumentType?.spec ?: ArgumentSpec.TEXT
                RequiredArgumentBuilder.argument<S, Any>(node.name, typeOf(spec))
                    .requires { invocation.allowed(it, node) }
                    .suggests(suggestionsFor(invocation))
                    .also { populate(it, node, invocation) }
            }
        }

    private fun bound(value: Double?, spec: ArgumentSpec): Double =
        requireNotNull(value) { "${spec.kind} argument needs both bounds" }

    // Types the portable parser accepts in more spellings than Brigadier stay as SpacelessWord
    // so the client never rejects input the server would take (e.g. `yes` for boolean, `1e3` for decimal).
    @Suppress("UNCHECKED_CAST")
    internal fun typeOf(spec: ArgumentSpec): ArgumentType<Any> = (when (spec.kind) {
        ArgumentSpec.Kind.INTEGER -> IntegerArgumentType.integer(
            bound(spec.minimum, spec).toInt(),
            bound(spec.maximum, spec).toInt(),
        )
        ArgumentSpec.Kind.LONG -> LongArgumentType.longArg(
            bound(spec.minimum, spec).toLong(),
            bound(spec.maximum, spec).toLong(),
        )
        ArgumentSpec.Kind.DECIMAL -> {
            val min = spec.minimum
            val max = spec.maximum
            if (min == null || max == null || (min == -Double.MAX_VALUE && max == Double.MAX_VALUE)) SpacelessWord
            else DoubleArgumentType.doubleArg(min, max)
        }
        ArgumentSpec.Kind.GREEDY -> StringArgumentType.greedyString()
        ArgumentSpec.Kind.BOOLEAN, ArgumentSpec.Kind.CHOICE, ArgumentSpec.Kind.TEXT -> SpacelessWord
    }) as ArgumentType<Any>

    private fun <S> suggestionsFor(invocation: NativeInvocation<S>): SuggestionProvider<S> =
        SuggestionProvider { context, builder ->
            val result = CompletableFuture<Suggestions>()
            // For greedy arguments (EXTRA_ARGUMENTS), builder.start points to the beginning of the entire
            // greedy portion. Adjust to the last token so the suggestion replaces only the word being typed.
            val lastSpace = builder.input.lastIndexOf(' ')
            val target = if (lastSpace >= builder.start) builder.createOffset(lastSpace + 1) else builder
            try {
                invocation.suggest(context.source, builder.input).whenComplete { values, error ->
                    if (error == null) values.orEmpty().forEach { target.suggest(it) }
                    result.complete(target.build())
                }
            } catch (_: Exception) {
                result.complete(target.build())
            }
            result
        }
}

// Brigadier's built-in word type stops at `/`, `:`, `;` and similar, breaking ids like `shop/main:config.yml`.
// Declared as greedy to the client so those chars are not painted red; the server reads it as one token.
@Suppress("UnstableApiUsage")
internal object SpacelessWord : CustomArgumentType<String, String> {
    override fun parse(reader: StringReader): String {
        val start = reader.cursor
        while (reader.canRead() && !reader.peek().isWhitespace()) reader.skip()
        return reader.string.substring(start, reader.cursor)
    }

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.greedyString()
}

internal data class CommandLine(val label: String, val arguments: List<String>) {
    companion object {
        fun of(input: String, keepTrailing: Boolean): CommandLine {
            val trimmed = input.trimStart().removePrefix("/")
            // keepTrailing: a trailing space produces an empty last argument, which tab completion needs.
            // Both paths collapse multiple spaces; keepTrailing re-appends the empty token manually.
            val collapsed = trimmed.trim().split(Regex(" +"))
            val parts = if (keepTrailing && trimmed.endsWith(' ')) collapsed + "" else collapsed
            return CommandLine(
                label = parts.first().substringAfterLast(':'),
                arguments = parts.drop(1),
            )
        }
    }
}
