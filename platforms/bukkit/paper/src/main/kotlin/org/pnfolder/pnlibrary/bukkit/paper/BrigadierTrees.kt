package org.pnfolder.pnlibrary.bukkit.paper

import com.mojang.brigadier.Command
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
import org.pnfolder.pnlibrary.api.commands.ArgumentSpec
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandNode
import org.pnfolder.pnlibrary.api.commands.CommandNodeKind
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** What a Brigadier node does when it is used: the portable router decides everything after the tree has matched. */
internal interface NativeInvocation<S> {
    /** Runs the command typed as [input], which is the whole line without the leading slash. */
    fun execute(source: S, input: String)

    /** Returns the suggestions for the partial argument at the end of [input]. */
    fun suggest(source: S, input: String): CompletionStage<List<String>>
}

/**
 * Builds the Brigadier tree of a portable command, so a modern client validates the arguments and shows the
 * usual red and gray hints while the shared router keeps doing the real work: permissions, custom messages,
 * parsing and execution. Literals and arguments mirror the portable tree one to one.
 */
internal object BrigadierTrees {
    /** Returns the root node of [definition]; its aliases are registered by the caller. */
    fun <S> build(definition: CommandDefinition, invocation: NativeInvocation<S>): LiteralCommandNode<S> {
        val root = LiteralArgumentBuilder.literal<S>(definition.name)
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
    }

    private fun <S> child(node: CommandNode, invocation: NativeInvocation<S>): ArgumentBuilder<S, *> {
        if (node.kind == CommandNodeKind.LITERAL) {
            return LiteralArgumentBuilder.literal<S>(node.name).also { populate(it, node, invocation) }
        }
        val spec = node.argumentType?.spec ?: ArgumentSpec.TEXT
        val builder = RequiredArgumentBuilder.argument<S, Any>(node.name, typeOf(spec))
            .suggests(suggestionsFor(invocation))
        populate(builder, node, invocation)
        return builder
    }

    /**
     * Maps a portable spec to a Brigadier type. Anything the portable parser accepts in more spellings than
     * Brigadier does (`yes` for a boolean, `1e3` for a decimal) stays a plain word, so the client never rejects
     * what the server would take.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun typeOf(spec: ArgumentSpec): ArgumentType<Any> = when (spec.kind) {
        ArgumentSpec.Kind.INTEGER ->
            IntegerArgumentType.integer(spec.minimum!!.toInt(), spec.maximum!!.toInt())
        ArgumentSpec.Kind.LONG ->
            LongArgumentType.longArg(spec.minimum!!.toLong(), spec.maximum!!.toLong())
        ArgumentSpec.Kind.DECIMAL ->
            if (spec.minimum == -Double.MAX_VALUE && spec.maximum == Double.MAX_VALUE) StringArgumentType.word()
            else DoubleArgumentType.doubleArg(spec.minimum!!, spec.maximum!!)
        ArgumentSpec.Kind.BOOLEAN, ArgumentSpec.Kind.CHOICE, ArgumentSpec.Kind.TEXT -> StringArgumentType.word()
    } as ArgumentType<Any>

    private fun <S> suggestionsFor(invocation: NativeInvocation<S>): SuggestionProvider<S> = SuggestionProvider { context, builder ->
        val result = CompletableFuture<Suggestions>()
        invocation.suggest(context.source, builder.input).whenComplete { values, error ->
            if (error != null) {
                result.complete(builder.build())
            } else {
                values.orEmpty().forEach { builder.suggest(it) }
                result.complete(builder.build())
            }
        }
        result
    }
}

/** A command line split into the label the player typed and its arguments. */
internal class CommandLine(val label: String, val arguments: List<String>) {
    companion object {
        /**
         * Splits [input]. A label such as `pnlibrary:pnevents` keeps only the command name. With [keepTrailing] a
         * line that ends with a space has an empty last argument, which is what tab completion needs.
         */
        fun of(input: String, keepTrailing: Boolean): CommandLine {
            val trimmed = input.trimStart().removePrefix("/")
            val parts = if (keepTrailing) trimmed.split(' ') else trimmed.trim().split(Regex(" +"))
            return CommandLine(parts.first().substringAfterLast(':'), parts.drop(1))
        }
    }
}
