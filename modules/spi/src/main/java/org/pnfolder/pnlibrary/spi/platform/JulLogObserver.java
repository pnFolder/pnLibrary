package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Forwards WARNING and SEVERE records of a {@code java.util.logging} tree to the shared log observation contract.
 *
 * <p>Plugin loggers are children of the platform logger, so one handler on the platform logger sees every
 * plugin's output. The source plugin is resolved from the record's logger name; unmatched records are attributed
 * to the fallback owner. Records written through {@link #runOwn} are skipped so the library's own messages never
 * come back as observations.
 */
public final class JulLogObserver {
    private static final int MIN_PARTIAL_LENGTH = 3;

    private final Supplier<Logger> platformLogger;
    private final Object fallbackOwner;
    private final Function<String, Object> resolveOwner;
    private final OwnLogScope ownLogs = new OwnLogScope();
    private volatile PlatformLogSink observer;
    private Logger attachedTo;

    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record == null || ownLogs.isActive()) {
                return;
            }
            if (record.getLevel().intValue() < Level.WARNING.intValue()) {
                return;
            }
            PlatformLogSink sink = observer;
            if (sink == null) {
                return;
            }
            LogLevel level = record.getLevel().intValue() >= Level.SEVERE.intValue() ? LogLevel.ERROR : LogLevel.WARNING;
            Object owner = resolveOwner.apply(record.getLoggerName());
            String message = record.getMessage() != null ? record.getMessage() : "Native platform error";
            sink.log(owner != null ? owner : fallbackOwner, level, message, record.getThrown());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    /**
     * @param platformLogger the platform's own logger, parent of every plugin logger
     * @param fallbackOwner owner of records whose logger matches no plugin
     * @param resolveOwner finds the plugin a logger name belongs to, or {@code null}
     */
    public JulLogObserver(
            @NotNull Supplier<Logger> platformLogger,
            @NotNull Object fallbackOwner,
            @NotNull Function<String, Object> resolveOwner) {
        this.platformLogger = platformLogger;
        this.fallbackOwner = fallbackOwner;
        this.resolveOwner = resolveOwner;
    }

    /** Starts forwarding to {@code value}, or stops when it is null. Repeated calls only swap the callback. */
    public synchronized void observe(@Nullable PlatformLogSink value) {
        observer = value;
        if (value != null && attachedTo == null) {
            attachedTo = platformLogger.get();
            attachedTo.addHandler(handler);
        } else if (value == null) {
            if (attachedTo != null) {
                attachedTo.removeHandler(handler);
            }
            attachedTo = null;
        }
    }

    /** Runs {@code block} while records produced by this thread are treated as the library's own. */
    public <T> T runOwn(@NotNull Supplier<T> block) {
        return ownLogs.run(block);
    }

    /** Platform log handler that writes each record to the logger {@code loggerFor} picks for its owner at the matching {@link #julLevel}. */
    @NotNull
    public PlatformLogSink logHandler(@NotNull Function<Object, Logger> loggerFor) {
        return (owner, level, message, error) -> loggerFor.apply(owner).log(julLevel(level), message, error);
    }

    /** The {@code java.util.logging} level a record of {@code level} is written at; errors are SEVERE. */
    @NotNull
    public static Level julLevel(@NotNull LogLevel level) {
        switch (level) {
            case WARNING:
                return Level.WARNING;
            case ERROR:
            case CRITICAL:
                return Level.SEVERE;
            default:
                return Level.INFO;
        }
    }

    /** The plugin among {@code plugins} whose name identifies {@code loggerName}, chosen by {@link #matchName}. */
    @Nullable
    public static <P> P matchPlugin(
            @Nullable String loggerName, @NotNull Collection<P> plugins, @NotNull Function<P, String> name) {
        List<String> names = new ArrayList<>();
        for (P plugin : plugins) {
            names.add(name.apply(plugin));
        }
        String match = matchName(loggerName, names);
        if (match == null) {
            return null;
        }
        for (P plugin : plugins) {
            if (name.apply(plugin).equals(match)) {
                return plugin;
            }
        }
        throw new java.util.NoSuchElementException("Collection contains no element matching the predicate.");
    }

    /**
     * Picks the name that identifies {@code loggerName}: an exact case-insensitive match first, otherwise
     * the longest name of at least three characters contained in it.
     */
    @Nullable
    public static String matchName(@Nullable String loggerName, @NotNull Collection<String> names) {
        if (loggerName == null || loggerName.trim().isEmpty()) {
            return null;
        }
        for (String name : names) {
            if (name.equalsIgnoreCase(loggerName)) {
                return name;
            }
        }
        String longest = null;
        for (String name : names) {
            boolean longer = longest == null || name.length() > longest.length();
            if (name.length() >= MIN_PARTIAL_LENGTH && longer && containsIgnoreCase(loggerName, name)) {
                longest = name;
            }
        }
        return longest;
    }

    private static boolean containsIgnoreCase(String text, String part) {
        for (int start = 0; start + part.length() <= text.length(); start++) {
            if (text.regionMatches(true, start, part, 0, part.length())) {
                return true;
            }
        }
        return false;
    }
}
