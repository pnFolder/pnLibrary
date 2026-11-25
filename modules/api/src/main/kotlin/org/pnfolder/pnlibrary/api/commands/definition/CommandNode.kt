package org.pnfolder.pnlibrary.api.commands.definition

import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.error.CommandError
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler
import java.util.Collections
import java.util.Locale

/**
 * One immutable route node in a portable command tree.
 *
 * @property kind structural role of this node
 * @property name literal value, argument name, or root command name
 * @property argumentType parser used by an argument node
 * @property permission optional permission required to enter this node
 * @property alsoPermissions other permissions that let a sender in just as [permission] does
 * @property consoleBypassesPermission whether the console ignores [permission]
 * @property availability contextual availability rule
 * @property execution execution callback for this route
 * @property suggestions suggestion callback for this route
 * @property isExecutable whether this route may be executed
 * @property children immutable child routes
 * @property noPermissionMessage text shown when the sender lacks [permission], or `null` to use the command's own
 * @property errorHandlers handlers for errors that occur at or below this node, by error class
 * @property senderRule who may enter this node
 * @property description what this node does and how, one line each; shown under the usage line when a sender stops here
 * @property acceptsExtraArguments whether an executable node still runs when the sender types words no child matches;
 * the handler then sees every typed word in [CommandContext.arguments]
 */
class CommandNode internal constructor(
    val kind: CommandNodeKind,
    val name: String,
    val argumentType: ArgumentType<*>?,
    val permission: String?,
    val consoleBypassesPermission: Boolean,
    val availability: CommandAvailability,
    val execution: CommandHandler,
    val suggestions: SuggestionHandler,
    val isExecutable: Boolean,
    children: List<CommandNode>,
    val noPermissionMessage: String? = null,
    errorHandlers: Map<Class<out CommandError>, CommandErrorHandler<*>> = emptyMap(),
    val senderRule: SenderRule = SenderRule.ANY,
    alsoPermissions: List<String> = emptyList(),
    description: List<String> = emptyList(),
    val acceptsExtraArguments: Boolean = false,
) {
    val description: List<String> = Collections.unmodifiableList(ArrayList(description))
    val alsoPermissions: List<String> = Collections.unmodifiableList(ArrayList(alsoPermissions))
    val errorHandlers: Map<Class<out CommandError>, CommandErrorHandler<*>> = Collections.unmodifiableMap(LinkedHashMap(errorHandlers))
    val children: List<CommandNode> = Collections.unmodifiableList(ArrayList(children))
}

/**
 * Copies the root of [definition] as a literal called [name] directly under another command's root.
 *
 * The handlers of every node in the copy see their arguments as if the mounted command stood alone: the literal
 * that leads to it is dropped, so `/pn events view abc` reaches the handler as `view abc`.
 */
internal fun mountedNode(name: String, definition: CommandDefinition): CommandNode =
    rebased(definition.root, normalizeNodeName(name))

private fun rebased(node: CommandNode, mountName: String? = null): CommandNode {
    val execution = node.execution
    val suggestions = node.suggestions
    return CommandNode(
        if (mountName != null) CommandNodeKind.LITERAL else node.kind,
        mountName ?: node.name,
        node.argumentType,
        node.permission,
        node.consoleBypassesPermission,
        node.availability,
        CommandHandler { context -> execution.execute(context.copy(arguments = context.arguments.drop(1))) },
        SuggestionHandler { context -> suggestions.suggest(context.copy(arguments = context.arguments.drop(1))) },
        node.isExecutable,
        node.children.map { rebased(it) },
        node.noPermissionMessage,
        node.errorHandlers,
        node.senderRule,
        node.alsoPermissions,
        node.description,
        node.acceptsExtraArguments,
    )
}

private val NODE_NAME = Regex("[a-z0-9][a-z0-9:_-]*")
private val ARGUMENT_NAME = Regex("[A-Za-z][A-Za-z0-9_-]*")

internal fun normalizeNodeName(value: String): String = value.trim().lowercase(Locale.ROOT).also {
    require(NODE_NAME.matches(it)) { "Command literal must match ${NODE_NAME.pattern}" }
}

internal fun normalizeArgumentName(value: String): String = value.trim().also {
    require(ARGUMENT_NAME.matches(it)) { "Command argument name must match ${ARGUMENT_NAME.pattern}" }
}
