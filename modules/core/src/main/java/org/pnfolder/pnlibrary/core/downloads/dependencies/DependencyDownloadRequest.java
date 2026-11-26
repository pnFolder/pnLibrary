package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;

/** Download request for missing plugin dependencies with the requirement each downloaded JAR must meet. */
public final class DependencyDownloadRequest {
    private final FileDownloads request;
    private final Map<String, ExternalPluginDependency> requirements;

    /** Creates the request; {@code requirements} is keyed by the key of each file. */
    public DependencyDownloadRequest(@NotNull FileDownloads request,
                                     @NotNull Map<String, ExternalPluginDependency> requirements) {
        this.request = Objects.requireNonNull(request, "request");
        this.requirements = Objects.requireNonNull(requirements, "requirements");
    }

    /** The files to download. */
    public @NotNull FileDownloads getRequest() {
        return request;
    }

    /** What each downloaded file must be, by the key of the file. */
    public @NotNull Map<String, ExternalPluginDependency> getRequirements() {
        return requirements;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DependencyDownloadRequest)) {
            return false;
        }
        DependencyDownloadRequest that = (DependencyDownloadRequest) other;
        return request.equals(that.request) && requirements.equals(that.requirements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, requirements);
    }

    @Override
    public String toString() {
        return "DependencyDownloadRequest(request=" + request + ", requirements=" + requirements + ")";
    }
}
