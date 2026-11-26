package org.pnfolder.pnlibrary.api.downloads;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;

/** Immutable collection of files registered as one download unit. */
public final class FileDownloads {
    private final Path dataDirectory;
    private final List<FileDownload> files;

    private FileDownloads(Builder builder) {
        this.dataDirectory = builder.dataDirectory == null ? null : builder.dataDirectory.toAbsolutePath().normalize();
        this.files = Collections.unmodifiableList(new ArrayList<FileDownload>(builder.files));
    }

    /** The optional plugin data-directory override. */
    public @Nullable Path getDataDirectory() {
        return dataDirectory;
    }

    /** The files owned by this unit. */
    public @NotNull List<FileDownload> getFiles() {
        return files;
    }

    /** Creates a new {@link Builder}. */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /** Fluent builder for a {@link FileDownloads} declaration. */
    public static final class Builder {
        private Path dataDirectory;
        private final List<FileDownload> files = new ArrayList<FileDownload>();

        private Builder() {
        }

        /** Overrides the plugin data directory used by data-folder destinations. */
        public @NotNull Builder dataDirectory(@NotNull Path value) {
            dataDirectory = value;
            return this;
        }

        /** Declares and validates a file under the unique {@code id}. */
        public @NotNull Builder file(@NotNull String id, @NotNull Consumer<FileDownload.Builder> configure) {
            FileDownload.Builder builder = new FileDownload.Builder(id);
            configure.accept(builder);
            FileDownload file = builder.build();
            for (FileDownload known : files) {
                if (known.getKey().equalsIgnoreCase(file.getKey())) {
                    throw new IllegalArgumentException("Duplicate file download: " + file.getKey());
                }
            }
            files.add(file);
            return this;
        }

        /**
         * Declares a file under {@code id} that is downloaded from {@code url} to {@code relativePath} in the data
         * folder, with everything else as usual.
         */
        public @NotNull Builder file(@NotNull String id, @NotNull String url, @NotNull String relativePath) {
            return file(id, file -> file.url(url).destination(DownloadDestination.DATA_FOLDER, relativePath));
        }

        /** Validates this declaration and creates an immutable download unit. */
        public @NotNull FileDownloads build() {
            if (files.isEmpty()) {
                throw new IllegalArgumentException("At least one file download is required");
            }
            return new FileDownloads(this);
        }
    }
}
