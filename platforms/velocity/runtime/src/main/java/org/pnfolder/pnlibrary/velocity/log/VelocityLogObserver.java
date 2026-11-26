package org.pnfolder.pnlibrary.velocity.log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.spi.platform.OwnLogScope;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogSink;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Forwards WARN and ERROR events of Velocity's Log4j2 backend to the shared log observation contract.
 *
 * <p>Velocity names a plugin's SLF4J logger after its main class, so an event is attributed to the plugin whose
 * main-class package is the longest prefix of the logger name, or whose id equals it; other events go to the
 * fallback owner. Events written through {@link #runOwn} are skipped. When Log4j is unavailable the observer
 * silently does nothing.
 */
public final class VelocityLogObserver {
    private static final String APPENDER_NAME = "pnLibrary-log-observer";

    private final Object fallbackOwner;
    private final Function<String, Object> pluginById;
    private final Supplier<Collection<Object>> pluginInstances;
    private final OwnLogScope ownLogs = new OwnLogScope();
    private volatile PlatformLogSink callback;
    private LoggerContext attachedContext;
    private Appender attachedAppender;

    /** Creates an observer that attributes events to the plugins of {@code pluginInstances}. */
    public VelocityLogObserver(@NotNull Object fallbackOwner, @NotNull Supplier<Collection<Object>> pluginInstances) {
        this(fallbackOwner, id -> null, pluginInstances);
    }

    /** Creates an observer that also finds a plugin by the id its logger is named after. */
    public VelocityLogObserver(
            @NotNull Object fallbackOwner,
            @NotNull Function<String, Object> pluginById,
            @NotNull Supplier<Collection<Object>> pluginInstances) {
        this.fallbackOwner = fallbackOwner;
        this.pluginById = pluginById;
        this.pluginInstances = pluginInstances;
    }

    /** Starts forwarding to {@code observer}, or stops when it is null. */
    public synchronized void observe(@Nullable PlatformLogSink observer) {
        callback = observer;
        if (observer != null && attachedContext == null) {
            attach();
        } else if (observer == null) {
            detach();
        }
    }

    /** Runs {@code block} while events produced by this thread are treated as the library's own. */
    public <T> T runOwn(@NotNull Supplier<T> block) {
        return ownLogs.run(block);
    }

    /** Returns the plugin whose main-class package is the longest prefix of {@code loggerName}. */
    @Nullable
    public Object resolveOwner(@Nullable String loggerName) {
        if (loggerName == null || loggerName.trim().isEmpty()) {
            return null;
        }
        // Velocity names a plugin's logger after its id when the plugin takes the injected logger.
        Object byId = pluginById.apply(loggerName);
        if (byId != null) {
            return byId;
        }
        Object best = null;
        int bestLength = -1;
        for (Object instance : pluginInstances.get()) {
            String className = instance.getClass().getName();
            int dot = className.lastIndexOf('.');
            String prefix = dot < 0 ? "" : className.substring(0, dot);
            boolean matches = prefix.indexOf('.') >= 0
                    && (loggerName.equals(className) || loggerName.startsWith(prefix + "."));
            if (matches && prefix.length() > bestLength) {
                best = instance;
                bestLength = prefix.length();
            }
        }
        return best;
    }

    private void attach() {
        try {
            if (!(LogManager.getContext(false) instanceof LoggerContext)) {
                return;
            }
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Appender appender = new AbstractAppender(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    accept(event);
                }
            };
            appender.start();
            context.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
            context.updateLoggers();
            attachedContext = context;
            attachedAppender = appender;
        } catch (LinkageError ignored) {
            // Log4j is not on the proxy class path; uncaught-exception observation still works.
        }
    }

    private void detach() {
        if (attachedContext == null) {
            return;
        }
        LoggerContext context = attachedContext;
        Appender appender = attachedAppender;
        attachedContext = null;
        attachedAppender = null;
        context.getConfiguration().getRootLogger().removeAppender(APPENDER_NAME);
        context.updateLoggers();
        appender.stop();
    }

    private void accept(LogEvent event) {
        if (ownLogs.isActive() || !event.getLevel().isMoreSpecificThan(Level.WARN)) {
            return;
        }
        LogLevel level = event.getLevel().isMoreSpecificThan(Level.ERROR) ? LogLevel.ERROR : LogLevel.WARNING;
        Object resolved = resolveOwner(event.getLoggerName());
        Object owner = resolved != null ? resolved : fallbackOwner;
        PlatformLogSink sink = callback;
        if (sink == null) {
            return;
        }
        String message = event.getMessage() != null ? event.getMessage().getFormattedMessage() : null;
        sink.log(owner, level, message != null ? message : "Native platform error", event.getThrown());
    }
}
