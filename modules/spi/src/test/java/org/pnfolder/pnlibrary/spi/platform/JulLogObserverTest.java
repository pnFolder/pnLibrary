package org.pnfolder.pnlibrary.spi.platform;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JulLogObserverTest {
    private static final class Seen {
        final Object owner;
        final LogLevel level;
        final String message;
        final Throwable error;

        Seen(Object owner, LogLevel level, String message, Throwable error) {
            this.owner = owner;
            this.level = level;
            this.message = message;
            this.error = error;
        }
    }

    private final Logger platform = Logger.getLogger("platform-" + UUID.randomUUID());
    private final Object fallback = new Object();
    private final Object plugin = new Object();
    private final List<Seen> seen = new ArrayList<>();
    private final JulLogObserver observer = new JulLogObserver(
            () -> platform, fallback, name -> "Plugin".equals(name) ? plugin : null);

    JulLogObserverTest() {
        platform.setUseParentHandlers(false);
    }

    private void start() {
        observer.observe((owner, level, message, error) -> seen.add(new Seen(owner, level, message, error)));
    }

    private List<LogLevel> levels() {
        List<LogLevel> levels = new ArrayList<>();
        for (Seen entry : seen) {
            levels.add(entry.level);
        }
        return levels;
    }

    private List<String> messages() {
        List<String> messages = new ArrayList<>();
        for (Seen entry : seen) {
            messages.add(entry.message);
        }
        return messages;
    }

    @Test
    void warningsAndErrorsFromChildLoggersAreForwardedWithPluginAttribution() {
        start();
        Logger child = Logger.getLogger(platform.getName() + ".child");
        child.setParent(platform);
        Logger pluginLogger = Logger.getLogger("Plugin");
        pluginLogger.setParent(platform);
        pluginLogger.setUseParentHandlers(true);
        IllegalStateException failure = new IllegalStateException("boom");

        child.warning("careful");
        pluginLogger.log(Level.SEVERE, "failed", failure);
        pluginLogger.info("ignored");

        assertEquals(Arrays.asList(LogLevel.WARNING, LogLevel.ERROR), levels());
        assertEquals(fallback, seen.get(0).owner);
        assertEquals(plugin, seen.get(1).owner);
        assertEquals(failure, seen.get(1).error);
    }

    @Test
    void ownLogCallsAreNotReportedBack() {
        start();
        observer.runOwn(() -> {
            platform.severe("internal");
            return null;
        });
        platform.severe("external");
        assertEquals(Arrays.asList("external"), messages());
    }

    @Test
    void stoppingRemovesTheHandlerAndRepeatedStartsAttachOnlyOne() {
        start();
        start();
        platform.warning("once");
        assertEquals(1, seen.size());

        observer.observe(null);
        platform.warning("after stop");
        assertEquals(1, seen.size());
    }

    @Test
    void nameMatchingPrefersExactThenLongestContainedName() {
        List<String> names = Arrays.asList("Vault", "Vault-Economy", "ab");
        assertEquals("Vault", JulLogObserver.matchName("vault", names));
        assertEquals("Vault-Economy", JulLogObserver.matchName("[Vault-Economy] sql", names));
        assertNull(JulLogObserver.matchName("about", names));
        assertNull(JulLogObserver.matchName(null, names));
    }

    @Test
    void pluginMatchingReturnsThePluginObjectWithTheIdentifyingName() {
        List<String[]> plugins = Arrays.asList(new String[] {"Vault", "1"}, new String[] {"Vault-Economy", "2"});
        assertEquals(plugins.get(1), JulLogObserver.matchPlugin("[Vault-Economy] sql", plugins, entry -> entry[0]));
        assertNull(JulLogObserver.matchPlugin("Other", plugins, entry -> entry[0]));
    }

    @Test
    void logHandlerWritesToTheOwnerLoggerAtTheMappedLevelAndForwardsToTheObserver() {
        start();
        List<LogRecord> records = new ArrayList<>();
        Logger ownerLogger = Logger.getLogger(platform.getName() + ".owner");
        ownerLogger.setParent(platform);
        ownerLogger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        IllegalStateException failure = new IllegalStateException("boom");
        PlatformLogSink handler = observer.logHandler(owner -> owner == plugin ? ownerLogger : platform);

        handler.log(plugin, LogLevel.ERROR, "failed", failure);
        handler.log(plugin, LogLevel.WARNING, "careful", null);
        handler.log(plugin, LogLevel.SUCCESS, "done", null);

        List<Level> levels = new ArrayList<>();
        for (LogRecord record : records) {
            levels.add(record.getLevel());
        }
        assertEquals(Arrays.asList(Level.SEVERE, Level.WARNING, Level.INFO), levels);
        assertEquals(failure, records.get(0).getThrown());
        assertEquals(Arrays.asList("failed", "careful"), messages());
    }
}
