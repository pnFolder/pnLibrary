package org.pnfolder.pnlibrary.api.commands

import net.kyori.adventure.text.Component
import java.util.Collections
import java.util.Locale
import java.util.function.Consumer
import java.util.function.Function

/** Who may enter a command node. */
enum class SenderRule { ANY, PLAYER, CONSOLE }

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
) {
    val alsoPermissions: List<String> = Collections.unmodifiableList(ArrayList(alsoPermissions))
    val errorHandlers: Map<Class<out CommandError>, CommandErrorHandler<*>> = Collections.unmodifiableMap(LinkedHashMap(errorHandlers))
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
    private var alsoPermissions: List<String> = emptyList()
    private var consoleBypassesPermission = false
    private var noPermissionMessage: String? = null
    @PublishedApi internal val errorHandlers = linkedMapOf<Class<out CommandError>, CommandErrorHandler<*>>()
    private var senderRule = SenderRule.ANY
    private var availability = CommandAvailability { true }
    private var execution = CommandHandler { completedExecution() }
    private var suggestions = SuggestionHandler { completedSuggestions(emptyList()) }
    private var executable = false
    private val children = mutableListOf<CommandNodeBuilder>()
    private val mounted = mutableListOf<CommandNode>()

    /** Requires the non-blank permission [value] for this node. */
    fun permission(value: String): CommandNodeBuilder = permission(value, *emptyArray<String>())

    /**
     * Requires the non-blank permission [value] for this node. With [others] a sender needs only one of them, so a
     * narrow permission such as `pnlibrary.updates.download` can open a node next to the wide `pnlibrary.admin`.
     */
    fun permission(value: String, vararg others: String): CommandNodeBuilder = apply {
        permission = value.trim().also { require(it.isNotEmpty()) { "Command permission must not be blank" } }
        alsoPermissions = others.map { other -> other.trim().also { require(it.isNotEmpty()) { "Command permission must not be blank" } } }
    }

    /** Sets the message shown to a sender who lacks this node's permission; it overrides the command's own text. */
    fun noPermissionMessage(value: String): CommandNodeBuilder = apply {
        noPermissionMessage = value.trim().also {
            require(it.isNotEmpty()) { "No-permission message must not be blank" }
        }
    }

    /**
     * Answers errors of class [type], and of every class derived from it, with [handler]: message the sender, play a
     * sound, notify staff, anything. The most specific level wins: a subcommand over the command, the command over the
     * module, the module over the plugin, the plugin over the library's standard answer. On one level the handler of
     * the nearest class wins, so `NoPermission` is answered before `CommandError`.
     */
    fun <E : CommandError> onError(type: Class<E>, handler: CommandErrorHandler<E>): CommandNodeBuilder = apply {
        errorHandlers[type] = handler
    }

    /** Answers errors of class [type] with the plain [message]; `{name}` fills in from [CommandError.placeholders]. */
    fun <E : CommandError> onError(type: Class<E>, message: String): CommandNodeBuilder = apply {
        val text = message.trim().also { require(it.isNotEmpty()) { "Error message must not be blank" } }
        errorHandlers[type] = CommandErrorHandler<E> { context, error -> context.sender.send(Component.text(error.format(text))) }
    }

    /**
     * Kotlin form: `onError<NoPermission> { error -> sender.send(...) }`. Inside the lambda `this` is the
     * [CommandContext], so `sender` is right there, and `error` has its own typed fields.
     */
    @JvmSynthetic
    inline fun <reified E : CommandError> onError(noinline handler: CommandContext.(E) -> Unit): CommandNodeBuilder =
        onError(E::class.java, CommandErrorHandler<E> { context, error -> context.handler(error) })

    /** Kotlin form with plain text: `onError<InvalidArgument>("«{value}» не подходит для {argument}")`. */
    @JvmSynthetic
    inline fun <reified E : CommandError> onError(message: String): CommandNodeBuilder = onError(E::class.java, message)

    /** Lets only players enter this node; the console gets the [PlayerOnly] answer. */
    fun playerOnly(): CommandNodeBuilder = apply { senderRule = SenderRule.PLAYER }

    /** Lets only the console enter this node; players get the [ConsoleOnly] answer. */
    fun consoleOnly(): CommandNodeBuilder = apply { senderRule = SenderRule.CONSOLE }

    /** Configures whether the console bypasses this node's permission. */
    @JvmOverloads
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

    internal fun putErrorHandler(type: Class<out CommandError>, handler: CommandErrorHandler<*>) {
        errorHandlers[type] = handler
    }

    internal fun addBuiltChild(child: CommandNodeBuilder) {
        children += child
    }

    internal fun addMounted(nodes: List<CommandNode>) {
        mounted += nodes
    }

    internal fun build(): CommandNode {
        validateChildren()
        return CommandNode(
            kind, name, argumentType, permission, consoleBypassesPermission, availability,
            execution, suggestions, executable, children.map(CommandNodeBuilder::build) + mounted, noPermissionMessage, errorHandlers, senderRule,
            alsoPermissions,
        )
    }

    private fun validateChildren() {
        val names = children.map { it.kind to it.name } + mounted.map { it.kind to it.name }
        val duplicate = names.groupBy { it }.entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) { "Duplicate command child '${duplicate?.key?.second}'" }
        require(children.count { it.kind == CommandNodeKind.ARGUMENT } <= 1) {
            "A command node cannot have multiple argument children"
        }
    }
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
    )
}

private val NODE_NAME = Regex("[a-z0-9][a-z0-9:_-]*")
private val ARGUMENT_NAME = Regex("[A-Za-z][A-Za-z0-9_-]*")

private fun normalizeNodeName(value: String): String = value.trim().lowercase(Locale.ROOT).also {
    require(NODE_NAME.matches(it)) { "Command literal must match ${NODE_NAME.pattern}" }
}

private fun normalizeArgumentName(value: String): String = value.trim().also {
    require(ARGUMENT_NAME.matches(it)) { "Command argument name must match ${ARGUMENT_NAME.pattern}" }
}
