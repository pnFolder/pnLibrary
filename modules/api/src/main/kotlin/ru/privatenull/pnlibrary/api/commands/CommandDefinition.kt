package ru.privatenull.pnlibrary.api.commands

import java.util.Collections
import java.util.LinkedHashSet
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Consumer
import java.util.function.Function

/** Asynchronous execution contract stored by an immutable command definition. */
fun interface CommandHandler {
    /** Executes a command invocation and completes when handling has finished. */
    fun execute(context: CommandContext): CompletionStage<Void>
}

/** Asynchronous suggestion contract stored by an immutable command definition. */
fun interface SuggestionHandler {
    /** Computes completion suggestions for the supplied parsing [context]. */
    fun suggest(context: CommandContext): CompletionStage<List<String>>
}

/**
 * Immutable command metadata and behavior registered through pnLibrary.
 *
 * @property name normalized primary command name
 * @property permission optional root permission
 * @property consoleBypassesPermission whether the console ignores the root permission
 * @property execution root execution callback
 * @property suggestions root suggestion callback
 * @property root immutable root of the parsed command tree
 * @property aliases normalized immutable command aliases
 */
class CommandDefinition internal constructor(
    val name: String,
    aliases: Set<String>,
    val permission: String?,
    val consoleBypassesPermission: Boolean,
    val execution: CommandHandler,
    val suggestions: SuggestionHandler,
    val root: CommandNode,
) {
    val aliases: Set<String> = Collections.unmodifiableSet(LinkedHashSet(aliases))

    /** Creates command-definition builders. */
    companion object {
        /** Creates a builder for Java callers. */
        @JvmStatic
        fun builder(name: String): CommandBuilder = CommandBuilder(name)
    }
}

/** Mutable construction DSL that produces an immutable [CommandDefinition]. */
class CommandBuilder internal constructor(name: String) {
    private val name = normalizeCommandName(name, "command name")
    private val aliases = linkedSetOf<String>()
    private var permission: String? = null
    private var consoleBypassesPermission: Boolean = false
    private var execution = CommandHandler { completedExecution() }
    private var executable = false
    private var suggestions = SuggestionHandler { completedSuggestions(emptyList()) }
    private var availability = CommandAvailability { true }
    private val children = mutableListOf<CommandNodeBuilder>()

    /** Adds normalized command aliases. */
    fun aliases(vararg values: String): CommandBuilder = apply {
        values.forEach { value ->
            val alias = normalizeCommandName(value, "command alias")
            require(alias != name) { "Command alias must differ from the primary name" }
            aliases += alias
        }
    }

    /** Requires the non-blank root permission [value]. */
    fun permission(value: String): CommandBuilder = apply {
        permission = value.trim().also {
            require(it.isNotEmpty()) { "Command permission must not be blank" }
        }
    }

    /** Configures whether the console bypasses the root permission. */
    fun consoleBypassesPermission(enabled: Boolean = true): CommandBuilder = apply {
        consoleBypassesPermission = enabled
    }

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

    /** Adds a literal child configured by the Kotlin receiver [configure]. */
    @JvmSynthetic
    fun literal(name: String, configure: CommandNodeBuilder.() -> Unit): CommandBuilder =
        literal(name, Consumer { builder -> builder.configure() })

    /** Adds a literal child configured by Java-friendly [configure]. */
    fun literal(name: String, configure: Consumer<CommandNodeBuilder>): CommandBuilder = apply {
        children += CommandNodeBuilder(CommandNodeKind.LITERAL, name).also(configure::accept)
    }

    /** Adds a typed argument child configured by the Kotlin receiver [configure]. */
    @JvmSynthetic
    fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        configure: CommandNodeBuilder.() -> Unit,
    ): CommandBuilder = argument(name, type, Consumer { builder -> builder.configure() })

    /** Adds a typed argument child configured by Java-friendly [configure]. */
    fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        configure: Consumer<CommandNodeBuilder>,
    ): CommandBuilder = apply {
        children += CommandNodeBuilder(CommandNodeKind.ARGUMENT, name, type).also(configure::accept)
    }

    /** Validates this tree and creates an immutable command definition. */
    fun build(): CommandDefinition {
        val rootBuilder = CommandNodeBuilder(CommandNodeKind.ROOT, name).apply {
            permission?.let(::permission)
            consoleBypassesPermission(consoleBypassesPermission)
            availableIf(availability)
            if (executable) executesAsync(execution)
            suggestsAsync(suggestions)
            children.forEach { child -> addBuiltChild(child) }
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
        )
    }
}

/** Builds one immutable portable command. */
@JvmSynthetic
fun command(name: String, configure: CommandBuilder.() -> Unit): CommandDefinition =
    CommandDefinition.builder(name).apply(configure).build()

/** Returns an already-completed command execution stage. */
fun completedExecution(): CompletionStage<Void> = CompletableFuture.completedFuture(null)

/** Returns an already-completed immutable suggestion stage. */
fun completedSuggestions(values: List<String>): CompletionStage<List<String>> =
    CompletableFuture.completedFuture(Collections.unmodifiableList(ArrayList(values)))

private val COMMAND_NAME = Regex("[a-z0-9][a-z0-9:_-]*")

private fun normalizeCommandName(value: String, field: String): String =
    value.trim().lowercase(Locale.ROOT).also {
        require(COMMAND_NAME.matches(it)) {
            "$field must match ${COMMAND_NAME.pattern}"
        }
    }
