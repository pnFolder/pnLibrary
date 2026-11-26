package org.pnfolder.pnlibrary.core.plugin;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.logging.MessageBox;
import org.pnfolder.pnlibrary.api.logging.PnLogger;

/** A logging service that counts the cards shown and lists the rows written. */
public final class LoggingRecorder implements LoggingService {
    public int genericMessages;
    public int startupMessages;
    public int shutdownMessages;
    public final List<String> rows = new ArrayList<>();

    @Override
    public @NotNull PnLogger logger(@NotNull Object owner, @NotNull String name) {
        return PluginRegistryFixture.emptyProxy(PnLogger.class);
    }

    @Override
    public @NotNull MessageBox box(@NotNull Object owner, @NotNull String title) {
        return new RecordingMessageBox(rows, () -> genericMessages++);
    }

    @Override
    public @NotNull MessageBox box(@NotNull Object owner, @NotNull String name, @NotNull String version) {
        return new RecordingMessageBox(rows, () -> startupMessages++);
    }

    @Override
    public @NotNull MessageBox shutdownBox(@NotNull Object owner, @NotNull String title) {
        return new RecordingMessageBox(rows, () -> shutdownMessages++);
    }

    private static final class RecordingMessageBox implements MessageBox {
        private final List<String> rows;
        private final Runnable onShow;

        private RecordingMessageBox(List<String> rows, Runnable onShow) {
            this.rows = rows;
            this.onShow = onShow;
        }

        @Override
        public @NotNull MessageBox ok(@NotNull String label, @NotNull String detail) {
            rows.add(label);
            return this;
        }

        @Override
        public @NotNull MessageBox warn(@NotNull String label, @NotNull String detail) {
            rows.add(label);
            return this;
        }

        @Override
        public @NotNull MessageBox skip(@NotNull String label, @NotNull String detail) {
            rows.add(label);
            return this;
        }

        @Override
        public @NotNull MessageBox fail(@NotNull String label, @NotNull String detail, @Nullable Throwable error) {
            rows.add(label);
            return this;
        }

        @Override
        public void show() {
            onShow.run();
        }
    }
}
