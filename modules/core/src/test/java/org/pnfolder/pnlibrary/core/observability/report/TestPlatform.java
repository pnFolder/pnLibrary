package org.pnfolder.pnlibrary.core.observability.report;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/** The smallest platform that the report tests need. */
final class TestPlatform implements PlatformAdapter {
    private final Path dataPath;

    TestPlatform(String prefix) throws IOException {
        this.dataPath = Files.createTempDirectory(prefix);
    }

    @NotNull
    @Override
    public PlatformType getType() {
        return PlatformType.BUKKIT;
    }

    @NotNull
    @Override
    public String getId() {
        return "test";
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
    public void close() {
    }
}
