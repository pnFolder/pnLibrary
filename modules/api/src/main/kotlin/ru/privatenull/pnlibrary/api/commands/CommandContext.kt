package ru.privatenull.pnlibrary.api.commands

/** One portable command invocation or suggestion request. */
data class CommandContext(
    val sender: CommandSender,
    val arguments: List<String>,
    val invokedAlias: String,
    val currentInput: String,
)
