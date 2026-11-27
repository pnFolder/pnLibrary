package org.pnfolder.pnlibrary.core.downloads;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.core.settings.LibrarySettings;

/** Administrator policy for downloads: which destinations are allowed. */
public final class DownloadConfiguration {
    private final Set<DownloadDestination> destinations;

    /** Creates a policy from its parts. */
    public DownloadConfiguration(@NotNull Set<DownloadDestination> destinations) {
        Set<DownloadDestination> copy = EnumSet.noneOf(DownloadDestination.class);
        copy.addAll(destinations);
        this.destinations = Collections.unmodifiableSet(copy);
    }

    /** The default policy: everything allowed. */
    public DownloadConfiguration() {
        this(EnumSet.of(DownloadDestination.DATA_FOLDER, DownloadDestination.CACHE));
    }

    /** The download policy in these settings (the {@code downloads.*} keys). */
    public static @NotNull DownloadConfiguration of(@NotNull LibrarySettings settings) {
        return new DownloadConfiguration(EnumSet.of(DownloadDestination.DATA_FOLDER, DownloadDestination.CACHE));
    }

    /** The places a file may be saved to. */
    public @NotNull Set<DownloadDestination> getDestinations() {
        return destinations;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DownloadConfiguration)) {
            return false;
        }
        DownloadConfiguration that = (DownloadConfiguration) other;
        return destinations.equals(that.destinations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinations);
    }

    @Override
    public String toString() {
        return "DownloadConfiguration(destinations=" + destinations + ")";
    }
}
