package org.pnfolder.pnlibrary.core.downloads;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.core.settings.LibrarySettings;

/** Administrator policy for downloads: whether they run, start automatically, and which destinations are allowed. */
public final class DownloadConfiguration {
    private final boolean enabled;
    private final boolean automatic;
    private final Set<DownloadDestination> destinations;

    /** Creates a policy from its parts. */
    public DownloadConfiguration(boolean enabled, boolean automatic, @NotNull Set<DownloadDestination> destinations) {
        this.enabled = enabled;
        this.automatic = automatic;
        Set<DownloadDestination> copy = EnumSet.noneOf(DownloadDestination.class);
        copy.addAll(destinations);
        this.destinations = Collections.unmodifiableSet(copy);
    }

    /** The default policy: everything allowed. */
    public DownloadConfiguration() {
        this(true, true, EnumSet.of(DownloadDestination.DATA_FOLDER, DownloadDestination.CACHE));
    }

    /** The download policy in these settings (the {@code downloads.*} keys). */
    public static @NotNull DownloadConfiguration of(@NotNull LibrarySettings settings) {
        return new DownloadConfiguration(settings.getDownloadsEnabled(), settings.getDownloadsAutomatic(),
            EnumSet.of(DownloadDestination.DATA_FOLDER, DownloadDestination.CACHE));
    }

    /** The same policy with the automatic switch set to {@code value}. */
    public @NotNull DownloadConfiguration withAutomatic(boolean value) {
        return new DownloadConfiguration(enabled, value, destinations);
    }

    /** Whether downloads run at all. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Whether downloads that a plugin starts itself are allowed. */
    public boolean isAutomatic() {
        return automatic;
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
        return enabled == that.enabled && automatic == that.automatic && destinations.equals(that.destinations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, automatic, destinations);
    }

    @Override
    public String toString() {
        return "DownloadConfiguration(enabled=" + enabled + ", automatic=" + automatic
            + ", destinations=" + destinations + ")";
    }
}
