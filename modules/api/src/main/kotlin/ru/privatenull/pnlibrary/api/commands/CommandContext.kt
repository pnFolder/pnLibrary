package ru.privatenull.pnlibrary.api.commands

/**
 * One portable command invocation or suggestion request.
 *
 * @property sender platform-neutral command sender
 * @property arguments raw tokens following the invoked command alias
 * @property invokedAlias command name or alias used for this invocation
 * @property currentInput complete input available at the current parsing stage
 * @property parsedValues immutable values produced by argument nodes parsed so far
 */
data class CommandContext(
    val sender: CommandSender,
    val arguments: List<String>,
    val invokedAlias: String,
    val currentInput: String,
    val parsedValues: Map<String, Any> = emptyMap(),
) {
    /** Returns a value parsed by the named argument node. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(name: String): T = parsedValues[name] as? T
        ?: throw IllegalArgumentException("No parsed command argument named '$name'")

    /** Returns whether a parsed argument named [name] is available. */
    fun contains(name: String): Boolean = parsedValues.containsKey(name)

    internal fun withParsedValues(values: Map<String, Any>, input: String = currentInput): CommandContext =
        copy(parsedValues = values, currentInput = input)
}
