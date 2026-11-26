package org.pnfolder.pnlibrary.updates.engine.transaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Durable paths and metadata for one journaled artifact. The field names and order are the on-disk journal format.
 */
public final class JournalArtifact {
    private final String component;
    private final String target;
    private final String staged;
    private final String backup;
    private final boolean targetExisted;
    private final String expectedVersion;
    private final String previousVersion;
    private final String installedTarget;
    private final JournalVersion expected;
    private final JournalVersion prior;
    private String backupSha256;

    public JournalArtifact(@NotNull String component, @NotNull String target, @NotNull String staged,
                           @NotNull String backup, boolean targetExisted) {
        this(component, target, staged, backup, targetExisted, null);
    }

    public JournalArtifact(@NotNull String component, @NotNull String target, @NotNull String staged,
                           @NotNull String backup, boolean targetExisted, @Nullable String expectedVersion) {
        this(component, target, staged, backup, targetExisted, expectedVersion, null, null, null, null, null);
    }

    /**
     * @param component normalized product identifier
     * @param target publication path
     * @param staged verified staging path
     * @param backup rollback backup path
     * @param targetExisted whether publication replaces an existing file
     * @param expectedVersion version expected during post-restart health verification
     * @param previousVersion version stored in the rollback backup, when known
     */
    public JournalArtifact(@NotNull String component, @NotNull String target, @NotNull String staged,
                           @NotNull String backup, boolean targetExisted, @Nullable String expectedVersion,
                           @Nullable String previousVersion, @Nullable String installedTarget,
                           @Nullable JournalVersion expected, @Nullable JournalVersion prior,
                           @Nullable String backupSha256) {
        this.component = component;
        this.target = target;
        this.staged = staged;
        this.backup = backup;
        this.targetExisted = targetExisted;
        this.expectedVersion = expectedVersion;
        this.previousVersion = previousVersion;
        this.installedTarget = installedTarget;
        this.expected = expected;
        this.prior = prior;
        this.backupSha256 = backupSha256;
    }

    @NotNull
    public String getComponent() {
        return component;
    }

    @NotNull
    public String getTarget() {
        return target;
    }

    @NotNull
    public String getStaged() {
        return staged;
    }

    @NotNull
    public String getBackup() {
        return backup;
    }

    public boolean getTargetExisted() {
        return targetExisted;
    }

    @Nullable
    public String getExpectedVersion() {
        return expectedVersion;
    }

    @Nullable
    public String getPreviousVersion() {
        return previousVersion;
    }

    @Nullable
    public String getInstalledTarget() {
        return installedTarget;
    }

    @Nullable
    public JournalVersion getExpected() {
        return expected;
    }

    @Nullable
    public JournalVersion getPrior() {
        return prior;
    }

    @Nullable
    public String getBackupSha256() {
        return backupSha256;
    }

    public void setBackupSha256(@Nullable String backupSha256) {
        this.backupSha256 = backupSha256;
    }

    /** A copy that publishes to {@code value} instead. */
    @NotNull
    public JournalArtifact withTarget(@NotNull String value) {
        return new JournalArtifact(component, value, staged, backup, targetExisted, expectedVersion, previousVersion,
            installedTarget, expected, prior, backupSha256);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JournalArtifact)) {
            return false;
        }
        JournalArtifact that = (JournalArtifact) other;
        return targetExisted == that.targetExisted && component.equals(that.component) && target.equals(that.target)
            && staged.equals(that.staged) && backup.equals(that.backup)
            && Objects.equals(expectedVersion, that.expectedVersion) && Objects.equals(previousVersion, that.previousVersion)
            && Objects.equals(installedTarget, that.installedTarget) && Objects.equals(expected, that.expected)
            && Objects.equals(prior, that.prior) && Objects.equals(backupSha256, that.backupSha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(component, target, staged, backup, targetExisted, expectedVersion, previousVersion,
            installedTarget, expected, prior, backupSha256);
    }

    @Override
    public String toString() {
        return "JournalArtifact(component=" + component + ", target=" + target + ", staged=" + staged
            + ", backup=" + backup + ", targetExisted=" + targetExisted + ", expectedVersion=" + expectedVersion
            + ", previousVersion=" + previousVersion + ", installedTarget=" + installedTarget
            + ", expected=" + expected + ", prior=" + prior + ", backupSha256=" + backupSha256 + ")";
    }
}
