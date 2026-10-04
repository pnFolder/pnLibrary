package ru.privatenull.pnlibrary.api.commands

import java.util.Collections
import java.util.function.Consumer
import java.util.function.Function

/** Structural role of a node in a command tree. */
enum class CommandNodeKind {
    ROOT,
    LITERAL,
    ARGUMENT
}

/** Synchronous access rule evaluated with values parsed before this node. */
fun interface CommandAvailability {
    /** Returns whether this node is visible and executable for [context]. */
    fun isAvailable(context: CommandContext): Boolean
}

/**
 * One immutable route node in a portable command tree.
 *
 * @property kind structural role of this node
 * @property name literal value, argument name, or root command name
 * @property argumentType parser used by an argument node
 * @property permission optional permission required to enter this node
 * @property consoleBypassesPermission whether the console ignores [permission]
 * @property availability contextual availability rule
 * @property execution execution callback for this route
 * @property suggestions suggestion callback for this route
 * @property isExecutable whether this route may be executed
 * @property children immutable child routes
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

    /** Requires the non-blank permission [value] for this node. */
    fun permission(value: String): CommandNodeBuilder = apply {
        permission = value.trim().also { require(it.isNotEmpty()) { "Command permission must not be blank" } }
    }

    /** Configures whether the console bypasses this node's permission. */
    fun consoleBypassesPermission(enabled: Boolean = true): CommandNodeBuilder = apply {
        consoleBypassesPermission = enabled
    }

    /** Restricts node availability using [rule]. */
    fun availableIf(rule: CommandAvailability): CommandNodeBuilder = apply { availability = rule }

    /** Installs a synchronous execution [handler]. */
    fun executes(handler: Consumer<CommandContext>): CommandNodeBuilder = apply {
        executable = true
        execution = CommandHandler { context ->
            handler.accept(context)
            completedExecution()
        }
    }

    /** Installs an asynchronous execution [handler]. */
    fun executesAsync(handler: CommandHandler): CommandNodeBuilder = apply {
        executable = true
        execution = handler
    }

    /** Installs a synchronous suggestion [handler]. */
    fun suggests(handler: Function<CommandContext, List<String>>): CommandNodeBuilder = apply {
        suggestions = SuggestionHandler { context -> completedSuggestions(handler.apply(context)) }
    }

    /** Installs an asynchronous suggestion [handler]. */
    fun suggestsAsync(handler: SuggestionHandler): CommandNodeBuilder = apply { suggestions = handler }

    /** Adds a literal child configured by the Kotlin receiver [configure]. */
    @JvmSynthetic
    fun literal(name: String, configure: CommandNodeBuilder.() -> Unit): CommandNodeBuilder =
        literal(name, Consumer { builder -> builder.configure() })

    /** Adds a literal child configured by Java-friendly [configure]. */
    fun literal(name: String, configure: Consumer<CommandNodeBuilder>): CommandNodeBuilder = apply {
        children += CommandNodeBuilder(CommandNodeKind.LITERAL, name).also(configure::accept)
    }

    /** Adds a typed argument child configured by the Kotlin receiver [configure]. */
    @JvmSynthetic
    fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        configure: CommandNodeBuilder.() -> Unit,
    ): CommandNodeBuilder = argument(name, type, Consumer { builder -> builder.configure() })

    /** Adds a typed argument child configured by Java-friendly [configure]. */
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

private fun normalizeNodeName(value: String): String = value.trim().lowercase(java.util.Locale.ROOT).also {
    require(NODE_NAME.matches(it)) { "Command literal must match ${NODE_NAME.pattern}" }
}

private fun normalizeArgumentName(value: String): String = value.trim().also {
    require(ARGUMENT_NAME.matches(it)) { "Command argument name must match ${ARGUMENT_NAME.pattern}" }
}
