package org.pnfolder.pnlibrary.core.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Platform that records what it was asked to print. */
final class TestPlatform implements PlatformAdapter {
    final List<Object[]> logged = new ArrayList<>();
    final List<String> lines = new ArrayList<>();
    private final Path dataPath;
    private final String name;

    TestPlatform(String name) {
        this.name = name;
        try {
            dataPath = Files.createTempDirectory("pnlibrary-log-test");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @Override public @NotNull PlatformType getType() {
        return PlatformType.BUKKIT;
    }

    @Override public @NotNull String getId() {
        return "test";
    }

    @Override public @NotNull Path getDataFolder() {
        return dataPath;
    }

    @Override public @NotNull Map<String, String> ownerDetails(@NotNull Object owner) {
        return Collections.singletonMap("name", name);
    }

    @Override public void executeGlobal(@NotNull Runnable task) {
        task.run();
    }

    @Override public void executeReply(@NotNull Object recipient, @NotNull Runnable task) {
        task.run();
    }

    @Override public void log(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message,
                              @Nullable Throwable error) {
        logged.add(new Object[] {message, error});
    }

    @Override public void console(@NotNull Object owner, @NotNull String message) {
        lines.add(message);
    }

    @Override public void close() {
    }
}
