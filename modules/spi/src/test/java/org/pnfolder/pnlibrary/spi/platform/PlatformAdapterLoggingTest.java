package org.pnfolder.pnlibrary.spi.platform;

import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogSink;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlatformAdapterLoggingTest {
    @Test
    void defaultLogImplementationForwardsArgumentsToTheConfiguredHandler() throws IOException {
        Object owner = new Object();
        IllegalStateException error = new IllegalStateException("failure");
        AtomicReference<Object> receivedOwner = new AtomicReference<>();
        AtomicReference<LogLevel> receivedLevel = new AtomicReference<>();
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        AtomicReference<Throwable> receivedError = new AtomicReference<>();
        TestPlatformAdapter adapter = new TestPlatformAdapter((actualOwner, level, message, actualError) -> {
            receivedOwner.set(actualOwner);
            receivedLevel.set(level);
            receivedMessage.set(message);
            receivedError.set(actualError);
        });

        adapter.log(owner, LogLevel.ERROR, "message", error);

        assertSame(owner, receivedOwner.get());
        assertEquals(LogLevel.ERROR, receivedLevel.get());
        assertEquals("message", receivedMessage.get());
        assertSame(error, receivedError.get());
    }

    private static final class TestPlatformAdapter implements PlatformAdapter {
        private final PlatformLogSink logHandler;
        private final Path dataFolder;

        TestPlatformAdapter(PlatformLogSink logHandler) throws IOException {
            this.logHandler = logHandler;
            this.dataFolder = Files.createTempDirectory("pnlibrary-platform-test");
        }

        @NotNull
        @Override
        public PlatformLogSink getLogHandler() {
            return logHandler;
        }

        @NotNull
        @Override
        public PlatformType getType() {
            return PlatformType.BUKKIT;
        }

        @NotNull
        @Override
        public Path getDataFolder() {
            return dataFolder;
        }

        @Override
        public void executeGlobal(@NotNull Runnable task) {
            task.run();
        }

        @Override
        public void executeReply(@NotNull Object recipient, @NotNull Runnable task) {
            task.run();
        }

        @Override
        public void close() {
        }
    }
}
