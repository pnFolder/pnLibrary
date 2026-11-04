package org.pnfolder.pnlibrary.core.commands

import org.pnfolder.pnlibrary.api.commands.ArgumentSpec
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandError
import org.pnfolder.pnlibrary.api.commands.CommandNode
import org.pnfolder.pnlibrary.api.commands.CommandNodeKind
import org.pnfolder.pnlibrary.api.commands.ConsoleOnly
import org.pnfolder.pnlibrary.api.commands.IncompleteCommand
import org.pnfolder.pnlibrary.api.commands.NoPermission
import org.pnfolder.pnlibrary.api.commands.PlayerOnly
import org.pnfolder.pnlibrary.api.commands.SenderRule
import org.pnfolder.pnlibrary.api.commands.UnknownArgument
import org.pnfolder.pnlibrary.api.commands.completedSuggestions
import org.pnfolder.pnlibrary.core.commands.events.ROOT
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.CompletionStage

/** Outcome of routing one invocation: a node that can run, or an error to answer. */
internal sealed class CommandRoute {
    /** [path] runs from the command's root down to [node]. */
    data class Executable(val node: CommandNode, val context: CommandContext, val path: List<CommandNode>) : CommandRoute()

    /** Something went wrong; [nodes] runs from the deepest node involved up to the command's root. */
    data class Problem(val error: CommandError, val nodes: List<CommandNode>) : CommandRoute()
}

/** Parses and completes immutable command trees without knowing the native server platform. */
internal class CommandTreeRouter {
    fun route(command: CommandDefinition, context: CommandContext): CommandRoute {
        var node = command.root
        val nodes = mutableListOf(node)
        val values = LinkedHashMap<String, Any>()
        var routed = context.copy(parsedValues = values.toMap())
        blocked(node, routed)?.let { return problem(it, nodes) }
        val path = mutableListOf(command.name)

        var index = 0
        while (index < context.arguments.size) {
            val token = context.arguments[index]
            val child = childFor(node, token)
            if (child == null) {
                if (node.acceptsExtraArguments && node.isExecutable) return CommandRoute.Executable(node, routed, nodes)
                return problem(UnknownArgument(usage(path, node, routed), token), nodes)
            }
            nodes += child
            blocked(child, routed)?.let { return problem(it, nodes) }
            if (child.kind == CommandNodeKind.ARGUMENT) {
                // A greedy argument takes the rest of the line as one value and ends the walk.
                val greedy = child.argumentType?.spec?.kind == ArgumentSpec.Kind.GREEDY
                val text = if (greedy) context.arguments.drop(index).joinToString(" ") else token
                val parsed = parse(child, text) ?: return problem(
                    (child.argumentType ?: ArgumentType.string()).error(usage(path, node, routed), child.name, text),
                    nodes,
                )
                values[child.name] = parsed
                path += "<${child.name}>"
                if (greedy) index = context.arguments.size - 1
            } else {
                path += child.name
            }
            node = child
            routed = context.copy(parsedValues = values.toMap())
            index++
        }

        return if (node.isExecutable) CommandRoute.Executable(node, routed, nodes)
        else problem(IncompleteCommand(usage(path, node, routed)), nodes)
    }

    /** Why the sender may not enter [node], or `null` when it may. */
    private fun blocked(node: CommandNode, context: CommandContext): CommandError? {
        if (!allowed(node, context)) return NoPermission(node.permission.orEmpty())
        return when (node.senderRule) {
            SenderRule.PLAYER -> if (context.sender.isConsole) PlayerOnly() else null
            SenderRule.CONSOLE -> if (!context.sender.isConsole) ConsoleOnly() else null
            SenderRule.ANY -> null
        }
    }

    private fun problem(error: CommandError, nodes: List<CommandNode>) = CommandRoute.Problem(error, nodes.asReversed())

    fun suggest(command: CommandDefinition, context: CommandContext): CompletionStage<List<String>> {
        var node = command.root
        val values = LinkedHashMap<String, Any>()
        var routed = context.copy(parsedValues = values.toMap())
        if (!allowed(node, routed)) return completedSuggestions(emptyList())
        val consumed = if (context.arguments.isEmpty()) emptyList() else context.arguments.dropLast(1)
        val partial = if (context.arguments.isEmpty()) context.currentInput else context.arguments.last()

        consumed.forEach { token ->
            // Everything typed after a greedy argument still belongs to that argument.
            if (node.argumentType?.spec?.kind == ArgumentSpec.Kind.GREEDY) return@forEach
            val child = childFor(node, token)
                ?: return if (node.acceptsExtraArguments) node.suggestions.suggest(routed.copy(currentInput = partial))
                else completedSuggestions(emptyList())
            if (!allowed(child, routed)) return completedSuggestions(emptyList())
            if (child.kind == CommandNodeKind.ARGUMENT) {
                val parsed = parse(child, token) ?: return completedSuggestions(emptyList())
                values[child.name] = parsed
            }
            node = child
            routed = context.copy(parsedValues = values.toMap(), currentInput = partial)
        }

        return lastSuggestions(node, routed.copy(currentInput = partial), partial)
    }

    /** A literal child that matches [token] wins over the argument child. */
    private fun childFor(node: CommandNode, token: String): CommandNode? =
        node.children.firstOrNull { it.kind == CommandNodeKind.LITERAL && it.name.equals(token, ignoreCase = true) }
            ?: node.children.firstOrNull { it.kind == CommandNodeKind.ARGUMENT }

    /** What can be typed after the last complete token: the literals of [node] and its argument's suggestions. */
    private fun lastSuggestions(node: CommandNode, suggestionContext: CommandContext, partial: String): CompletionStage<List<String>> {
        val literals = node.children.asSequence()
            .filter { it.kind == CommandNodeKind.LITERAL }
            .filter { allowed(it, suggestionContext) }
            .map { it.name }
            .filter { it.startsWith(partial, ignoreCase = true) }
            .toList()
        val argument = node.children.firstOrNull { it.kind == CommandNodeKind.ARGUMENT }
            ?.takeIf { allowed(it, suggestionContext) }
        val stage = when {
            argument != null -> argument.suggestions.suggest(suggestionContext).thenApply { it + builtInSuggestions(argument) }
            node.children.isEmpty() || node.acceptsExtraArguments -> node.suggestions.suggest(suggestionContext)
            else -> completedSuggestions(emptyList())
        }
        return stage.thenApply { dynamic ->
            (literals + dynamic.filter { it.startsWith(partial, ignoreCase = true) }).distinct()
        }
    }

    /** The accepted values of a choice or boolean argument, offered even when the command suggests nothing itself. */
    private fun builtInSuggestions(argument: CommandNode): List<String> {
        val spec = argument.argumentType?.spec ?: return emptyList()
        return when (spec.kind) {
            ArgumentSpec.Kind.CHOICE -> spec.choices
            ArgumentSpec.Kind.BOOLEAN -> listOf("true", "false")
            else -> emptyList()
        }
    }

    private fun allowed(node: CommandNode, context: CommandContext): Boolean {
        val permission = node.permission
        val permissionAllowed = permission == null ||
            (context.sender.isConsole && node.consoleBypassesPermission) ||
            context.sender.hasPermission(permission) ||
            node.alsoPermissions.any { context.sender.hasPermission(it) }
        return permissionAllowed && node.availability.isAvailable(context)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(node: CommandNode, token: String): Any? =
        (node.argumentType as? ArgumentType<Any>)?.parse(token)

    private fun usage(path: List<String>, node: CommandNode, context: CommandContext): String {
        val accessible = node.children.filter { allowed(it, context) }
        val suffix = when {
            accessible.isEmpty() -> ""
            accessible.size == 1 -> " " + display(accessible.single())
            else -> " {" + accessible.joinToString("|") { display(it) } + "}"
        }
        return "/${path.joinToString(" ")}$suffix"
    }

    private fun display(node: CommandNode): String = when (node.kind) {
        CommandNodeKind.ARGUMENT -> "<${node.name}>"
        else -> node.name.lowercase(Locale.ROOT)
    }
}
