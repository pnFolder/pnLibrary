package org.pnfolder.pnlibrary.api.commands.definition

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.error.CommandError
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler
import java.util.function.Consumer
import java.util.function.Function

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
    private var description: List<String> = emptyList()
    private var availability = CommandAvailability { true }
    private var execution = CommandHandler { completedExecution() }
    private var suggestions = SuggestionHandler { completedSuggestions(emptyList()) }
    private var executable = false
    private var acceptsExtraArguments = false
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
        errorHandlers[type] = CommandErrorHandler<E> { context, error -> context.sender.sendMessage(Component.text(error.format(text))) }
    }

    /**
     * Kotlin form: `onError<NoPermission> { error -> sender.sendMessage(...) }`. Inside the lambda `this` is the
     * [CommandContext], so `sender` is right there, and `error` has its own typed fields.
     */
    @JvmSynthetic
    inline fun <reified E : CommandError> onError(noinline handler: CommandContext.(E) -> Unit): CommandNodeBuilder =
        onError(E::class.java, CommandErrorHandler<E> { context, error -> context.handler(error) })

    /** Kotlin form with plain text: `onError<InvalidArgument>("«{value}» не подходит для {argument}")`. */
    @JvmSynthetic
    inline fun <reified E : CommandError> onError(message: String): CommandNodeBuilder = onError(E::class.java, message)

    /** Says in a few lines what this node does and how it works; a sender who stops here is shown them under the usage. */
    fun description(vararg lines: String): CommandNodeBuilder = apply { description = lines.map(String::trim).filter { it.isNotEmpty() } }

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

    /**
     * Lets this executable node run when the sender types words that none of its children match, instead of answering
     * with an unknown-argument error. The handler reads the words from [CommandContext.arguments]. A modern Paper
     * client is told the node takes free text, so it does not reject them early.
     */
    fun acceptsExtraArguments(): CommandNodeBuilder = apply { acceptsExtraArguments = true }

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

    /** Adds a fixed-word child node configured by the Kotlin receiver [configure]. */
    @JvmSynthetic
    fun literal(name: String, configure: CommandNodeBuilder.() -> Unit): CommandNodeBuilder =
        literal(name, Consumer { builder -> builder.configure() })

    /** Adds a fixed-word child node configured by the Java [configure] callback. */
    fun literal(name: String, configure: Consumer<CommandNodeBuilder>): CommandNodeBuilder = apply {
        children += CommandNodeBuilder(CommandNodeKind.LITERAL, name).also(configure::accept)
    }

    /** Adds a typed argument child node configured by the Kotlin receiver [configure]. */
    @JvmSynthetic
    fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        configure: CommandNodeBuilder.() -> Unit,
    ): CommandNodeBuilder = argument(name, type, Consumer { builder -> builder.configure() })

    /** Adds a typed argument child node configured by the Java [configure] callback. */
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
            alsoPermissions, description, acceptsExtraArguments,
        )
    }

    private fun validateChildren() {
        val names = children.map { it.kind to it.name } + mounted.map { it.kind to it.name }
        val duplicate = names.groupBy { it }.entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) { "Duplicate command child '${duplicate?.key?.second}'" }
        require(children.count { it.kind == CommandNodeKind.ARGUMENT } <= 1) {
            "A command node cannot have multiple argument children"
        }
        children.firstOrNull { it.argumentType?.spec?.kind == ArgumentSpec.Kind.GREEDY && it.children.isNotEmpty() }?.let {
            throw IllegalArgumentException("Greedy argument '${it.name}' must be the last node of its branch")
        }
    }
}
