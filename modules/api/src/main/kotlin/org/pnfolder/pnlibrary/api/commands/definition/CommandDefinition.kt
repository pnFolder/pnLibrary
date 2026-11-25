package org.pnfolder.pnlibrary.api.commands.definition

import java.util.Collections
import java.util.LinkedHashSet
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

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
 * @property noPermissionMessage text shown to a sender who lacks the permission, or `null` for the default
 */
class CommandDefinition internal constructor(
    val name: String,
    aliases: Set<String>,
    val permission: String?,
    val consoleBypassesPermission: Boolean,
    val execution: CommandHandler,
    val suggestions: SuggestionHandler,
    val root: CommandNode,
    val noPermissionMessage: String? = null,
) {
    val aliases: Set<String> = Collections.unmodifiableSet(LinkedHashSet(aliases))

    companion object {
        /** Starts a command builder for the root label [name]; the entry point for Java callers. */
        @JvmStatic
        fun builder(name: String): CommandBuilder = CommandBuilder(name)
    }
}

/** Builds a [CommandDefinition] for the root label [name] with the Kotlin receiver [configure]. */
@JvmSynthetic
fun command(name: String, configure: CommandBuilder.() -> Unit): CommandDefinition =
    CommandDefinition.builder(name).apply(configure).build()

/** Returns an already-completed command execution stage. */
fun completedExecution(): CompletionStage<Void> = CompletableFuture.completedFuture(null)

/** Returns an already-completed immutable suggestion stage. */
fun completedSuggestions(values: List<String>): CompletionStage<List<String>> =
    CompletableFuture.completedFuture(Collections.unmodifiableList(ArrayList(values)))

private val COMMAND_NAME = Regex("[a-z0-9][a-z0-9:_-]*")

/** An alias may also be spelled in any script, such as a Cyrillic `обмен`. */
internal val ALIAS_NAME = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}:_-]*")

internal fun normalizeCommandName(value: String, field: String, pattern: Regex = COMMAND_NAME): String =
    value.trim().lowercase(Locale.ROOT).also {
        require(pattern.matches(it)) {
            "$field must match ${pattern.pattern}"
        }
    }
