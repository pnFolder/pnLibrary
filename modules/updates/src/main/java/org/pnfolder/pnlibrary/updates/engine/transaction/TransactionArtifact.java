package org.pnfolder.pnlibrary.updates.engine.transaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactSpecification;

import java.nio.file.Path;
import java.util.Objects;

/** One artifact participating in an atomic update transaction. */
public final class TransactionArtifact {
    private final ArtifactSpecification specification;
    private final Path source;
    private final Path target;
    private final Path rollbackSource;
    private final String previousVersion;
    private final ApiVersionRange previousApi;
    private final Path installedTarget;

    /** An artifact whose rollback source is its own target. */
    public TransactionArtifact(@NotNull ArtifactSpecification specification, @NotNull Path source, @NotNull Path target) {
        this(specification, source, target, target);
    }

    public TransactionArtifact(@NotNull ArtifactSpecification specification, @NotNull Path source, @NotNull Path target,
                               @NotNull Path rollbackSource) {
        this(specification, source, target, rollbackSource, null, null, null);
    }

    /**
     * @param specification trusted verification metadata
     * @param source downloaded source file
     * @param target final publication path
     * @param rollbackSource current artifact preserved for rollback
     * @param previousVersion version preserved in the backup, when known
     * @param previousApi API range of the preserved version, when known
     * @param installedTarget the installed jar this artifact replaces, when known
     */
    public TransactionArtifact(@NotNull ArtifactSpecification specification, @NotNull Path source, @NotNull Path target,
                               @NotNull Path rollbackSource, @Nullable String previousVersion,
                               @Nullable ApiVersionRange previousApi, @Nullable Path installedTarget) {
        this.specification = Objects.requireNonNull(specification, "specification");
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.rollbackSource = rollbackSource;
        this.previousVersion = previousVersion;
        this.previousApi = previousApi;
        this.installedTarget = installedTarget;
    }

    @NotNull
    public ArtifactSpecification getSpecification() {
        return specification;
    }

    @NotNull
    public Path getSource() {
        return source;
    }

    @NotNull
    public Path getTarget() {
        return target;
    }

    @NotNull
    public Path getRollbackSource() {
        return rollbackSource;
    }

    @Nullable
    public String getPreviousVersion() {
        return previousVersion;
    }

    @Nullable
    public ApiVersionRange getPreviousApi() {
        return previousApi;
    }

    @Nullable
    public Path getInstalledTarget() {
        return installedTarget;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransactionArtifact)) {
            return false;
        }
        TransactionArtifact that = (TransactionArtifact) other;
        return specification.equals(that.specification) && source.equals(that.source) && target.equals(that.target)
            && rollbackSource.equals(that.rollbackSource) && Objects.equals(previousVersion, that.previousVersion)
            && Objects.equals(previousApi, that.previousApi) && Objects.equals(installedTarget, that.installedTarget);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specification, source, target, rollbackSource, previousVersion, previousApi, installedTarget);
    }

    @Override
    public String toString() {
        return "TransactionArtifact(specification=" + specification + ", source=" + source + ", target=" + target
            + ", rollbackSource=" + rollbackSource + ", previousVersion=" + previousVersion
            + ", previousApi=" + previousApi + ", installedTarget=" + installedTarget + ")";
    }
}
