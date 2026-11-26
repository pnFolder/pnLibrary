package org.pnfolder.pnlibrary.bukkit.commands;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

/** Emits synthetic failures used to exercise diagnostics deduplication and cause-chain reporting; {@code report} confirms each one. */
final class BukkitDiagnosticTestErrors {
    private static final int DEFAULT_REPEATS = 10;
    private static final int MAX_REPEATS = 1_000;

    private final Plugin plugin;
    private final PnLibrary library;

    BukkitDiagnosticTestErrors(Plugin plugin, PnLibrary library) {
        this.plugin = plugin;
        this.library = library;
    }

    private PnLogger logger() {
        return library.getLogging().logger(plugin, "diagnostic-test");
    }

    /** Logs one error with a fresh id, to try the diagnostics pipeline. */
    void unique(Consumer<String> report) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        logger().error("Unique diagnostic test error [" + id + "]", new IllegalStateException("Generated unique failure [" + id + "]"));
        report.accept("Создана уникальная тестовая ошибка: " + id);
    }

    /** Logs the same error {@code requested} times (default and maximum are bounded) to try error grouping. */
    void repeated(Consumer<String> report, Integer requested) {
        int count = requested == null ? DEFAULT_REPEATS : Math.min(Math.max(requested, 1), MAX_REPEATS);
        PnLogger logger = logger();
        for (int index = 0; index < count; index++) {
            logger.error("Repeated diagnostic test error", new IllegalStateException("Generated repeated failure"));
        }
        report.accept("Одинаковая тестовая ошибка вызвана " + count + " раз.");
    }

    /** Logs an error with a cause chain, to try how causes are shown. */
    void chained(Consumer<String> report) {
        IllegalArgumentException root = new IllegalArgumentException("Invalid test database response");
        SQLException database = new SQLException("Test query execution failed", root);
        CompletionException completion = new CompletionException("Test asynchronous operation failed", database);
        logger().error("Chained diagnostic test error", completion);
        report.accept("Создана тестовая ошибка с полной цепочкой причин.");
    }
}
