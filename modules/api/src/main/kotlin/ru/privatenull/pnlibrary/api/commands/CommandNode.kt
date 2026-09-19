package ru.privatenull.pnlibrary.api.commands

import java.util.Collections
import java.util.function.Consumer
import java.util.function.Function

enum class CommandNodeKind { ROOT, LITERAL, ARGUMENT }

/** Synchronous access rule evaluated with values parsed before this node. */
fun interface CommandAvailability {
    fun isAvailable(context: CommandContext): Boolean
}

/** One immutable route node in a portable command tree. */
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
) {
    val children: List<CommandNode> = Collections.unmodifiableList(ArrayList(children))
}

/** Builder shared by literal and argument nodes. */
class CommandNodeBuilder internal constructor(
    private val kind: CommandNodeKind,
    name: String,
    private val argumentType: ArgumentType<*>? = null,
) {
    private val name = when (kind) {
        CommandNodeKind.ROOT -> name
        CommandNodeKind.LITERAL -> normalizeNodeName(name)
        CommandNodeKind.ARGUMENT -> normalizeArgumentName(name)
    }
    private var permission: String? = null
    private var consoleBypassesPermission = false
    private var availability = CommandAvailability { true }
    private var execution = CommandHandler { completedExecution() }
    private var suggestions = SuggestionHandler { completedSuggestions(emptyList()) }
    private var executable = false
    private val children = mutableListOf<CommandNodeBuilder>()

    fun permission(value: String): CommandNodeBuilder = apply {
        permission = value.trim().also { require(it.isNotEmpty()) { "Command permission must not be blank" } }
    }

    fun consoleBypassesPermission(enabled: Boolean = true): CommandNodeBuilder = apply {
        consoleBypassesPermission = enabled
    }

    fun availableIf(rule: CommandAvailability): CommandNodeBuilder = apply { availability = rule }

    fun executes(handler: Consumer<CommandContext>): CommandNodeBuilder = apply {
        executable = true
        execution = CommandHandler { context -> handler.accept(context); completedExecution() }
    }

    fun executesAsync(handler: CommandHandler): CommandNodeBuilder = apply {
        executable = true
        execution = handler
    }

    fun suggests(handler: Function<CommandContext, List<String>>): CommandNodeBuilder = apply {
        suggestions = SuggestionHandler { context -> completedSuggestions(handler.apply(context)) }
    }

    fun suggestsAsync(handler: SuggestionHandler): CommandNodeBuilder = apply { suggestions = handler }

    fun literal(name: String, configure: CommandNodeBuilder.() -> Unit): CommandNodeBuilder = apply {
        children += CommandNodeBuilder(CommandNodeKind.LITERAL, name).apply(configure)
    }

    fun literal(name: String, configure: Consumer<CommandNodeBuilder>): CommandNodeBuilder = apply {
        children += CommandNodeBuilder(CommandNodeKind.LITERAL, name).also(configure::accept)
    }

    fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        configure: CommandNodeBuilder.() -> Unit,
    ): CommandNodeBuilder = apply {
        children += CommandNodeBuilder(CommandNodeKind.ARGUMENT, name, type).apply(configure)
    }

    fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        configure: Consumer<CommandNodeBuilder>,
    ): CommandNodeBuilder = apply {
        children += CommandNodeBuilder(CommandNodeKind.ARGUMENT, name, type).also(configure::accept)
    }

    internal fun addBuiltChild(child: CommandNodeBuilder) {
        children += child
    }

    internal fun build(): CommandNode {
        validateChildren()
        return CommandNode(
            kind, name, argumentType, permission, consoleBypassesPermission, availability,
            execution, suggestions, executable, children.map(CommandNodeBuilder::build),
        )
    }

    private fun validateChildren() {
        val duplicate = children.groupBy { it.kind to it.name }.entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) { "Duplicate command child '${duplicate?.key?.second}'" }
        require(children.count { it.kind == CommandNodeKind.ARGUMENT } <= 1) {
            "A command node cannot have multiple argument children"
        }
    }
}

private val NODE_NAME = Regex("[a-z0-9][a-z0-9:_-]*")
private val ARGUMENT_NAME = Regex("[A-Za-z][A-Za-z0-9_-]*")

private fun normalizeNodeName(value: String): String = value.trim().lowercase().also {
    require(NODE_NAME.matches(it)) { "Command literal must match ${NODE_NAME.pattern}" }
}

private fun normalizeArgumentName(value: String): String = value.trim().also {
    require(ARGUMENT_NAME.matches(it)) { "Command argument name must match ${ARGUMENT_NAME.pattern}" }
}
