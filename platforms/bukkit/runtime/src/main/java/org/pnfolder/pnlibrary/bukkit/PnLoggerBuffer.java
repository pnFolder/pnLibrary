package org.pnfolder.pnlibrary.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.logging.PnLogger;

/**
 * Routes log calls to {@link PnLogger} when the library has started. Before that, outputs immediately to the native
 * console logger and buffers the entry. On {@link #flush()}, buffered entries are replayed via
 * {@link PnLogger#record} at their original level, going to the journal only, with no second console line.
 */
public final class PnLoggerBuffer {
    private final Logger nativeLogger;
    private final Supplier<PnLogger> source;
    private final List<Entry> entries = new ArrayList<>();

    public PnLoggerBuffer(Logger nativeLogger, Supplier<PnLogger> source) {
        this.nativeLogger = nativeLogger;
        this.source = source;
    }

    public void log(LogLevel level, String message) {
        log(level, message, null);
    }

    public void log(LogLevel level, String message, Throwable error) {
        PnLogger logger = source.get();
        if (logger != null) {
            logger.log(level, message, error);
            return;
        }
        Level javaLevel = Level.INFO;
        if (level == LogLevel.WARNING) {
            javaLevel = Level.WARNING;
        } else if (level == LogLevel.ERROR || level == LogLevel.CRITICAL) {
            javaLevel = Level.SEVERE;
        }
        if (error != null) {
            nativeLogger.log(javaLevel, message, error);
        } else {
            nativeLogger.log(javaLevel, message);
        }
        entries.add(new Entry(level, message, error));
    }

    public void flush() {
        PnLogger logger = source.get();
        if (logger == null) {
            return;
        }
        for (Entry entry : entries) {
            logger.record(entry.level, entry.message, entry.error);
        }
        entries.clear();
    }

    private static final class Entry {
        private final LogLevel level;
        private final String message;
        private final Throwable error;

        Entry(LogLevel level, String message, Throwable error) {
            this.level = level;
            this.message = message;
            this.error = error;
        }
    }
}
