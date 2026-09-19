package ru.privatenull.pnlibrary.core.commands

import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandNode
import ru.privatenull.pnlibrary.api.commands.CommandNodeKind
import ru.privatenull.pnlibrary.api.commands.completedSuggestions
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.CompletionStage

internal sealed class CommandRoute {
    data class Executable(val node: CommandNode, val context: CommandContext) : CommandRoute()
    data class Invalid(val usage: String) : CommandRoute()
    data object Denied : CommandRoute()
}

/** Parses and completes immutable command trees without knowing the native server platform. */
internal class CommandTreeRouter {
    fun route(command: CommandDefinition, context: CommandContext): CommandRoute {
        var node = command.root
        val values = LinkedHashMap<String, Any>()
        var routed = context.copy(parsedValues = values.toMap())
        if (!allowed(node, routed)) return CommandRoute.Denied
        val path = mutableListOf(command.name)

        context.arguments.forEach { token ->
            val literal = node.children.firstOrNull {
                it.kind == CommandNodeKind.LITERAL && it.name.equals(token, ignoreCase = true)
            }
            val child = literal ?: node.children.firstOrNull { it.kind == CommandNodeKind.ARGUMENT }
                ?: return CommandRoute.Invalid(usage(path, node))
            if (!allowed(child, routed)) return CommandRoute.Denied
            if (child.kind == CommandNodeKind.ARGUMENT) {
                val parsed = parse(child, token) ?: return CommandRoute.Invalid(usage(path, node))
                values[child.name] = parsed
                path += "<${child.name}>"
            } else {
                path += child.name
            }
            node = child
            routed = context.copy(parsedValues = values.toMap())
        }

        return if (node.isExecutable) CommandRoute.Executable(node, routed)
        else CommandRoute.Invalid(usage(path, node))
    }

    fun suggest(command: CommandDefinition, context: CommandContext): CompletionStage<List<String>> {
        var node = command.root
        val values = LinkedHashMap<String, Any>()
        var routed = context.copy(parsedValues = values.toMap())
        if (!allowed(node, routed)) return completedSuggestions(emptyList())
        val consumed = if (context.arguments.isEmpty()) emptyList() else context.arguments.dropLast(1)
        val partial = if (context.arguments.isEmpty()) context.currentInput else context.arguments.last()

        consumed.forEach { token ->
            val literal = node.children.firstOrNull {
                it.kind == CommandNodeKind.LITERAL && it.name.equals(token, ignoreCase = true)
            }
            val child = literal ?: node.children.firstOrNull { it.kind == CommandNodeKind.ARGUMENT }
                ?: return completedSuggestions(emptyList())
            if (!allowed(child, routed)) return completedSuggestions(emptyList())
            if (child.kind == CommandNodeKind.ARGUMENT) {
                val parsed = parse(child, token) ?: return completedSuggestions(emptyList())
                values[child.name] = parsed
            }
            node = child
            routed = context.copy(parsedValues = values.toMap(), currentInput = partial)
        }

        val suggestionContext = routed.copy(currentInput = partial)
        val literals = node.children.asSequence()
            .filter { it.kind == CommandNodeKind.LITERAL }
            .filter { allowed(it, suggestionContext) }
            .map { it.name }
            .filter { it.startsWith(partial, ignoreCase = true) }
            .toList()
        val argument = node.children.firstOrNull { it.kind == CommandNodeKind.ARGUMENT }
            ?.takeIf { allowed(it, suggestionContext) }
        val stage = when {
            argument != null -> argument.suggestions.suggest(suggestionContext)
            node.children.isEmpty() -> node.suggestions.suggest(suggestionContext)
            else -> completedSuggestions(emptyList())
        }
        return stage.thenApply { dynamic ->
            (literals + dynamic.filter { it.startsWith(partial, ignoreCase = true) }).distinct()
        }
    }

    private fun allowed(node: CommandNode, context: CommandContext): Boolean {
        val permission = node.permission
        val permissionAllowed = permission == null ||
            (context.sender.isConsole && node.consoleBypassesPermission) ||
            context.sender.hasPermission(permission)
        return permissionAllowed && node.availability.isAvailable(context)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(node: CommandNode, token: String): Any? =
        (node.argumentType as? ru.privatenull.pnlibrary.api.commands.ArgumentType<Any>)?.parse(token)

    private fun usage(path: List<String>, node: CommandNode): String {
        val suffix = when {
            node.children.isEmpty() -> ""
            node.children.size == 1 -> " " + display(node.children.single())
            else -> " {" + node.children.joinToString("|") { display(it) } + "}"
        }
        return "/${path.joinToString(" ")}$suffix"
    }

    private fun display(node: CommandNode): String = when (node.kind) {
        CommandNodeKind.ARGUMENT -> "<${node.name}>"
        else -> node.name.lowercase(Locale.ROOT)
    }
}
