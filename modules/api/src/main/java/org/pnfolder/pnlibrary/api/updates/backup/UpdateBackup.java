package org.pnfolder.pnlibrary.api.updates.backup;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;

/** One retained rollback set; paths and backup contents are intentionally not exposed. */
public final class UpdateBackup {
    private final UUID id;
    private final Instant createdAt;
    private final UUID planId;
    private final List<UpdateBackupComponent> components;
    private final int configurationFiles;
    private final List<UpdateBackupItem> items;

    /**
     * Creates the description.
     *
     * @param id exact transaction identity required for rollback
     * @param createdAt UTC creation time, or null for legacy backups without a recorded time
     * @param planId originating plan, or null for legacy backups
     * @param components the complete component set restored together
     * @param configurationFiles number of validated configuration files retained with this set; zero when unavailable
     * @param items what the saved files are: kind, name and size
     */
    public UpdateBackup(@NotNull UUID id, @Nullable Instant createdAt, @Nullable UUID planId,
                        @NotNull List<UpdateBackupComponent> components, int configurationFiles,
                        @NotNull List<UpdateBackupItem> items) {
        this.id = id;
        this.createdAt = createdAt;
        this.planId = planId;
        this.components = UpdateValidation.frozen(components);
        this.configurationFiles = configurationFiles;
        this.items = UpdateValidation.frozen(items);
    }

    /** A backup with no saved configuration files that does not describe its files one by one. */
    public UpdateBackup(@NotNull UUID id, @Nullable Instant createdAt, @Nullable UUID planId,
                        @NotNull List<UpdateBackupComponent> components) {
        this(id, createdAt, planId, components, 0);
    }

    /** A backup that does not describe its files one by one. */
    public UpdateBackup(@NotNull UUID id, @Nullable Instant createdAt, @Nullable UUID planId,
                        @NotNull List<UpdateBackupComponent> components, int configurationFiles) {
        this(id, createdAt, planId, components, configurationFiles, Collections.<UpdateBackupItem>emptyList());
    }

    /** Exact transaction identity required for rollback. */
    @NotNull
    public UUID getId() {
        return id;
    }

    /** UTC creation time, or null for legacy backups without a recorded time. */
    @Nullable
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Originating plan, or null for legacy backups. */
    @Nullable
    public UUID getPlanId() {
        return planId;
    }

    /** Immutable description of the complete component set restored together. */
    @NotNull
    public List<UpdateBackupComponent> getComponents() {
        return components;
    }

    /** Number of validated configuration files retained with this set; zero when unavailable. */
    public int getConfigurationFiles() {
        return configurationFiles;
    }

    /** What the saved files are: kind, name and size, so a menu can show an administrator what a copy holds. */
    @NotNull
    public List<UpdateBackupItem> getItems() {
        return items;
    }
}
