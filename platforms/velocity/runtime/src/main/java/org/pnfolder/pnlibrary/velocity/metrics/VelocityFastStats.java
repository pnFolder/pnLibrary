package org.pnfolder.pnlibrary.velocity.metrics;

import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens FastStats sessions on Velocity. FastStats is compiled for Java 21 and is loaded by name, never referenced
 * directly, so a Java 17 proxy never links it: the version check rejects the run before the class is touched.
 */
final class VelocityFastStats {
    private static final String PROVIDER = "org.pnfolder.pnlibrary.velocity.faststats.FastStatsVelocityProvider";
    private static final int REQUIRED_JAVA = 21;

    private final Logger nativeLogger = LoggerFactory.getLogger(VelocityFastStats.class);
    private final ProxyServer server;
    private final Path dataDirectory;

    VelocityFastStats(ProxyServer server, Path dataDirectory) {
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    /** The session of the plugin instance {@code owner} for the project {@code token}. */
    PluginMetrics open(Object owner, String token) {
        if (Runtime.version().feature() < REQUIRED_JAVA) {
            throw new IllegalStateException("FastStats on Velocity requires Java " + REQUIRED_JAVA + " or newer");
        }
        try {
            Class<?> type = Class.forName(PROVIDER, true, getClass().getClassLoader());
            Object provider = type.getConstructor().newInstance();
            return (PluginMetrics) type.getMethod("open", Object.class, ProxyServer.class, Logger.class, Path.class, String.class)
                    .invoke(provider, owner, server, nativeLogger, dataDirectory, token);
        } catch (InvocationTargetException error) {
            return sneakyThrow(error.getTargetException());
        } catch (ReflectiveOperationException error) {
            return sneakyThrow(error);
        }
    }

    // The caller declares no checked exceptions, and the cause must reach it unchanged.
    @SuppressWarnings("unchecked")
    private static <E extends Throwable, R> R sneakyThrow(Throwable error) throws E {
        throw (E) error;
    }
}
