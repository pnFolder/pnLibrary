package ru.privatenull.pnlibrary.bukkit.commands

import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CompletionException

/** Emits synthetic failures used to exercise diagnostics deduplication and cause-chain reporting. */
internal class BukkitDiagnosticTestErrors(
    private val plugin: Plugin,
    private val library: PnLibrary,
) {
    private val logger get() = library.logging.logger(plugin, "diagnostic-test")

    /** Emits a uniquely identifiable failure. */
    fun unique(sender: CommandSender) {
        val id = UUID.randomUUID().toString().substring(0, 8)
        logger.error("Unique diagnostic test error [$id]", IllegalStateException("Generated unique failure [$id]"))
        sender.sendMessage("§aСоздана уникальная тестовая ошибка: §f$id")
    }

    /** Emits the same failure repeatedly to exercise deduplication. */
    fun repeated(sender: CommandSender, requested: Int?) {
        val count = requested?.coerceIn(1, MAX_REPEATS) ?: DEFAULT_REPEATS
        val logger = logger
        repeat(count) { logger.error("Repeated diagnostic test error", IllegalStateException("Generated repeated failure")) }
        sender.sendMessage("§aОдинаковая тестовая ошибка вызвана §f$count §aраз.")
    }

    /** Emits a nested failure to exercise cause-chain reporting. */
    fun chained(sender: CommandSender) {
        val root = IllegalArgumentException("Invalid test database response")
        val database = SQLException("Test query execution failed", root)
        val completion = CompletionException("Test asynchronous operation failed", database)
        logger.error("Chained diagnostic test error", completion)
        sender.sendMessage("§aСоздана тестовая ошибка с полной цепочкой причин.")
    }

    private companion object {
        const val DEFAULT_REPEATS = 10
        const val MAX_REPEATS = 1_000
    }
}
