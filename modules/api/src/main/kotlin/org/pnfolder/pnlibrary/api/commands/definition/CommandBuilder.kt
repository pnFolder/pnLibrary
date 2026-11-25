package org.pnfolder.pnlibrary.api.commands.definition

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.error.CommandError
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler
import java.util.function.Consumer
import java.util.function.Function

/** Mutable construction DSL that produces an immutable [CommandDefinition]. */
class CommandBuilder internal constructor(name: String) {
    private val name = normalizeCommandName(name, "command name")
    private val aliases = linkedSetOf<String>()
    private var permission: String? = null
    private var alsoPermissions: List<String> = emptyList()
    private var consoleBypassesPermission: Boolean = false
    private var noPermissionMessage: String? = null
    @PublishedApi internal val errorHandlers = linkedMapOf<Class<out CommandError>, CommandErrorHandler<*>>()
    private var senderRule = SenderRule.ANY
    private var execution = CommandHandler { completedExecution() }
    private var executable = false
    private var acceptsExtraArguments = false
    private var suggestions = SuggestionHandler { completedSuggestions(emptyList()) }
    private var availability = CommandAvailability { true }
    private val children = mutableListOf<CommandNodeBuilder>()
    private val mounted = mutableListOf<CommandNode>()

    /** Adds alternative root labels that run the same command. */
    fun aliases(vararg values: String): CommandBuilder = apply {
        values.forEach { value ->
            val alias = normalizeCommandName(value, "command alias", ALIAS_NAME)
            require(alias != name) { "Command alias must differ from the primary name" }
            aliases += alias
        }
    }

    /** Requires the non-blank root permission [value]. */
    fun permission(value: String): CommandBuilder = permission(value, *emptyArray<String>())

    /** Requires the non-blank root permission [value]; with [others] a sender needs only one of them. */
    fun permission(value: String, vararg others: String): CommandBuilder = apply {
        permission = value.trim().also {
            require(it.isNotEmpty()) { "Command permission must not be blank" }
        }
        alsoPermissions = others.map { other -> other.trim().also { require(it.isNotEmpty()) { "Command permission must not be blank" } } }
    }

    /**
     * Sets the message shown to a sender who lacks the root permission, for example
     * `noPermissionMessage("У вас нет права на /pn events. Попросите администратора.")`.
     */
    fun noPermissionMessage(value: String): CommandBuilder = apply {
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
    fun <E : CommandError> onError(type: Class<E>, handler: CommandErrorHandler<E>): CommandBuilder = apply {
        errorHandlers[type] = handler
    }

    /** Answers errors of class [type] with the plain [message]; `{name}` fills in from [CommandError.placeholders]. */
    fun <E : CommandError> onError(type: Class<E>, message: String): CommandBuilder = apply {
        val text = message.trim().also { require(it.isNotEmpty()) { "Error message must not be blank" } }
        errorHandlers[type] = CommandErrorHandler<E> { context, error -> context.sender.sendMessage(Component.text(error.format(text))) }
    }

    /**
     * Kotlin form: `onError<NoPermission> { error -> sender.sendMessage(...) }`. Inside the lambda `this` is the
     * [CommandContext], so `sender` is right there, and `error` has its own typed fields.
     */
    @JvmSynthetic
    inline fun <reified E : CommandError> onError(noinline handler: CommandContext.(E) -> Unit): CommandBuilder =
        onError(E::class.java, CommandErrorHandler<E> { context, error -> context.handler(error) })

    /** Kotlin form with plain text: `onError<InvalidArgument>("«{value}» не подходит для {argument}")`. */
    @JvmSynthetic
    inline fun <reified E : CommandError> onError(message: String): CommandBuilder = onError(E::class.java, message)

    /** Lets only players use this command; the console gets the [PlayerOnly] answer. */
    fun playerOnly(): CommandBuilder = apply { senderRule = SenderRule.PLAYER }

    /** Lets only the console use this command; players get the [ConsoleOnly] answer. */
    fun consoleOnly(): CommandBuilder = apply { senderRule = SenderRule.CONSOLE }

    /** Configures whether the console bypasses the root permission. */
    @JvmOverloads
    fun consoleBypassesPermission(enabled: Boolean = true): CommandBuilder = apply {
        consoleBypassesPermission = enabled
    }

    /**
     * Lets the root run when the sender types words that none of its children match, instead of answering with an
     * unknown-argument error; the handler reads them from [CommandContext.arguments].
     */
    fun acceptsExtraArguments(): CommandBuilder = apply { acceptsExtraArguments = true }

    /** Installs a synchronous root execution [handler]. */
    fun executes(handler: Consumer<CommandContext>): CommandBuilder = apply {
        executable = true
        execution = CommandHandler { context ->
            handler.accept(context)
            completedExecution()
        }
    }

    /** Installs an asynchronous root execution [handler]. */
    fun executesAsync(handler: CommandHandler): CommandBuilder = apply {
        executable = true
        execution = handler
    }

    /** Installs a synchronous root suggestion [handler]. */
    fun suggests(handler: Function<CommandContext, List<String>>): CommandBuilder = apply {
        suggestions = SuggestionHandler { context -> completedSuggestions(handler.apply(context)) }
    }

    /** Installs an asynchronous root suggestion [handler]. */
    fun suggestsAsync(handler: SuggestionHandler): CommandBuilder = apply {
        suggestions = handler
    }

    /** Restricts root availability using [rule]. */
    fun availableIf(rule: CommandAvailability): CommandBuilder = apply { availability = rule }

    /** Adds a fixed-word subcommand configured by the Kotlin receiver [configure]. */
    @JvmSynthetic
    fun literal(name: String, configure: CommandNodeBuilder.() -> Unit): CommandBuilder =
        literal(name, Consumer { builder -> builder.configure() })

    /** Adds a fixed-word subcommand configured by the Java [configure] callback. */
    fun literal(name: String, configure: Consumer<CommandNodeBuilder>): CommandBuilder = apply {
        children += CommandNodeBuilder(CommandNodeKind.LITERAL, name).also(configure::accept)
    }

    /** Adds a typed argument node configured by the Kotlin receiver [configure]. */
    @JvmSynthetic
    fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        configure: CommandNodeBuilder.() -> Unit,
    ): CommandBuilder = argument(name, type, Consumer { builder -> builder.configure() })

    /** Adds a typed argument node configured by the Java [configure] callback. */
    fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        configure: Consumer<CommandNodeBuilder>,
    ): CommandBuilder = apply {
        children += CommandNodeBuilder(CommandNodeKind.ARGUMENT, name, type).also(configure::accept)
    }

    /**
     * Adds the whole tree of [definition] as the literal [name], so a separate command can live under this one.
     *
     * The mounted node keeps its permission, sender rule, availability, handlers and children. The aliases of
     * [definition] are not carried over.
     */
    fun mount(name: String, definition: CommandDefinition): CommandBuilder = apply {
        mounted += mountedNode(name, definition)
    }

    /** Validates this tree and creates an immutable command definition. */
    fun build(): CommandDefinition {
        val commandHandlers = errorHandlers.toMap() // read before the node builder, which has a map of the same name
        val rootBuilder = CommandNodeBuilder(CommandNodeKind.ROOT, name).apply {
            permission?.let { permission(it, *alsoPermissions.toTypedArray()) }
            noPermissionMessage?.let(::noPermissionMessage)
            commandHandlers.forEach { (type, handler) -> putErrorHandler(type, handler) }
            when (senderRule) {
                SenderRule.PLAYER -> playerOnly()
                SenderRule.CONSOLE -> consoleOnly()
                SenderRule.ANY -> Unit
            }
            consoleBypassesPermission(consoleBypassesPermission)
            availableIf(availability)
            if (executable) executesAsync(execution)
            if (acceptsExtraArguments) acceptsExtraArguments()
            suggestsAsync(suggestions)
            children.forEach { child -> addBuiltChild(child) }
            addMounted(mounted)
        }
        val root = rootBuilder.build()
        return CommandDefinition(
            name = name,
            aliases = aliases,
            permission = permission,
            consoleBypassesPermission = consoleBypassesPermission,
            execution = execution,
            suggestions = suggestions,
            root = root,
            noPermissionMessage = noPermissionMessage,
        )
    }
}
