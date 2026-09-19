package ru.privatenull.pnlibrary.api.commands

/** One portable command invocation or suggestion request. */
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

    fun contains(name: String): Boolean = parsedValues.containsKey(name)

    internal fun withParsedValues(values: Map<String, Any>, input: String = currentInput): CommandContext =
        copy(parsedValues = values, currentInput = input)
}
