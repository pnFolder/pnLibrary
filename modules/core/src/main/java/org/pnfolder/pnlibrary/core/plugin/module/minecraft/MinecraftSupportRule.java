package org.pnfolder.pnlibrary.core.plugin.module.minecraft;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.compat.declaration.MinecraftVersions;
import org.pnfolder.pnlibrary.api.compat.declaration.NewerAction;
import org.pnfolder.pnlibrary.api.compat.declaration.UnsupportedAction;

/** What a plugin or module declared about Minecraft: the versions and what to do outside them. */
public final class MinecraftSupportRule {
    private volatile MinecraftVersions versions = MinecraftVersions.NONE;
    private volatile UnsupportedAction unsupportedAction;
    private volatile NewerAction newerAction;
    private volatile Boolean forceAllowed;

    public @NotNull MinecraftVersions getVersions() {
        return versions;
    }

    public void setVersions(@NotNull MinecraftVersions versions) {
        this.versions = versions;
    }

    /** What to do outside the declared versions, or {@code null} to take the plugin's choice, then {@code DISABLE_MODULE}. */
    public @Nullable UnsupportedAction getUnsupportedAction() {
        return unsupportedAction;
    }

    public void setUnsupportedAction(@Nullable UnsupportedAction action) {
        this.unsupportedAction = action;
    }

    /** What to do above the newest declared version, or {@code null} to take the plugin's choice, then {@code ALLOW_WITH_WARNING}. */
    public @Nullable NewerAction getNewerAction() {
        return newerAction;
    }

    public void setNewerAction(@Nullable NewerAction action) {
        this.newerAction = action;
    }

    /** Whether "start anyway" is allowed, or {@code null} to take the plugin's choice, then {@code true}. */
    public @Nullable Boolean getForceAllowed() {
        return forceAllowed;
    }

    public void setForceAllowed(boolean allowed) {
        this.forceAllowed = allowed;
    }

    public boolean isDeclared() {
        return versions.isDeclared();
    }

    public @Nullable String getDescription() {
        return versions.getDescription();
    }

    public synchronized void add(@NotNull MinecraftVersions more) {
        versions = versions.plus(more);
    }
}
