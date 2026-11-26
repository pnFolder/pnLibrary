package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.DownloadState;

/** The missing dependencies of one plugin; {@link #getCode()} confirms their installation from the console. */
public final class MissingGroup {
    private final String owner;
    private final String code;
    private final List<MissingDependency> items;
    private final DownloadRegistration registration;

    /** Creates a group; {@code registration} is null when no dependency has a direct link. */
    public MissingGroup(@NotNull String owner, @NotNull String code, @NotNull List<MissingDependency> items,
                        @Nullable DownloadRegistration registration) {
        this.owner = owner;
        this.code = code;
        this.items = Collections.unmodifiableList(new ArrayList<MissingDependency>(items));
        this.registration = registration;
    }

    /** The plugin that needs them. */
    public @NotNull String getOwner() {
        return owner;
    }

    /** The code the administrator types to install them. */
    public @NotNull String getCode() {
        return code;
    }

    /** The missing plugins. */
    public @NotNull List<MissingDependency> getItems() {
        return items;
    }

    /** Whether the library can download them itself. */
    public boolean getInstallable() {
        return registration != null;
    }

    /** Every direct address of every file. */
    public @NotNull List<URI> getAddresses() {
        List<URI> addresses = new ArrayList<URI>();
        for (MissingDependency item : items) {
            addresses.addAll(item.getSources());
        }
        return addresses;
    }

    /** Not yet staged: still worth telling the administrator about. */
    public boolean getPending() {
        if (registration == null) {
            return true;
        }
        List<DownloadSnapshot> snapshots = registration.snapshots();
        if (snapshots.isEmpty()) {
            return true;
        }
        for (DownloadSnapshot snapshot : snapshots) {
            if (snapshot.getState() != DownloadState.STAGED) {
                return true;
            }
        }
        return false;
    }

    /** Downloads the files now. */
    public @NotNull CompletionStage<List<DownloadSnapshot>> install() {
        if (registration == null) {
            throw new IllegalArgumentException("У этих зависимостей нет прямой ссылки");
        }
        return registration.downloadNow();
    }
}
