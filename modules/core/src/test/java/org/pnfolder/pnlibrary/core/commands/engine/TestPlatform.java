package org.pnfolder.pnlibrary.core.commands.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

/** A platform that runs tasks at once and keeps the errors that were logged. */
final class TestPlatform implements PlatformAdapter {
    final List<Throwable> errors = new ArrayList<Throwable>();
    private final PlatformCommandAdapter commands;
    private final Path dataPath;

    TestPlatform(PlatformCommandAdapter commands) {
        this.commands = commands;
        try {
            this.dataPath = Files.createTempDirectory("pnlibrary-command-test");
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    @NotNull
    @Override
    public PlatformCommandAdapter getCommandAdapter() {
        return commands;
    }

    @NotNull
    @Override
    public PlatformType getType() {
        return PlatformType.BUKKIT;
    }

    @NotNull
    @Override
    public Path getDataFolder() {
        return dataPath;
    }

    @NotNull
    @Override
    public Map<String, Object> diagnosticDetails(boolean includeSensitive) {
        return Collections.emptyMap();
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
    public void log(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message, @Nullable Throwable error) {
        if (error != null) {
            errors.add(error);
        }
    }

    @Override
    public void close() {
    }
}
