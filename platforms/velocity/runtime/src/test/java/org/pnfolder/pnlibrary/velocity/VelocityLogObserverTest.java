package org.pnfolder.pnlibrary.velocity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.velocity.log.VelocityLogObserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Checks attribution of Log4j events to the plugin that wrote them. */
class VelocityLogObserverTest {
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

    private static final class PluginA {
    }

    private static final class PluginB {
    }

    private final Object fallback = new Object();
    private final PluginA pluginA = new PluginA();
    private final PluginB pluginB = new PluginB();
    private final List<Seen> seen = new ArrayList<>();
    private final VelocityLogObserver observer =
            new VelocityLogObserver(fallback, () -> Arrays.<Object>asList(pluginA, pluginB));
    private final String loggerName = "org.pnfolder.pnlibrary.velocity.test." + System.nanoTime();

    private void start() {
        observer.observe((owner, level, message, error) -> seen.add(new Seen(owner, level, message, error)));
    }

    private List<String> messages() {
        List<String> messages = new ArrayList<>();
        for (Seen entry : seen) {
            messages.add(entry.message);
        }
        return messages;
    }

    @Test
    void errorsAreForwardedAndOwnLogCallsAreSkipped() {
        start();
        Logger logger = LogManager.getLogger(loggerName);
        IllegalStateException failure = new IllegalStateException("boom");

        logger.error("failed", failure);
        observer.runOwn(() -> {
            logger.error("internal");
            return null;
        });
        logger.info("quiet");

        assertEquals(Collections.singletonList("failed"), messages());
        assertEquals(LogLevel.ERROR, seen.get(0).level);
        assertSame(failure, seen.get(0).error);
        observer.observe(null);
    }

    @Test
    void stoppingDetachesTheAppender() {
        start();
        observer.observe(null);

        LogManager.getLogger(loggerName).error("after stop");

        assertEquals(Collections.<String>emptyList(), messages());
    }

    @Test
    void loggerNamesResolveToThePluginWithTheLongestMatchingPackage() {
        String name = PluginA.class.getName();
        String pkg = name.substring(0, name.lastIndexOf('.'));

        assertSame(pluginA, observer.resolveOwner(name));
        assertSame(pluginA, observer.resolveOwner(pkg + ".storage.Pool"));
        assertNull(observer.resolveOwner("com.velocitypowered.proxy.Velocity"));
        assertNull(observer.resolveOwner(null));
    }
}
