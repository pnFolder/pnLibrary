package org.pnfolder.pnlibrary.spi.platform.plugin;

import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared view of the native plugins installed on one platform.
 *
 * <p>An adapter only says how to list its plugins, how to recognize a native owner and how to describe one plugin;
 * owner metadata, installed versions and owner validation then behave the same way on every platform.
 */
public final class PlatformPlugins<P> {
    private final String platformName;
    private final Supplier<Collection<P>> installed;
    private final Function<Object, P> resolve;
    private final Function<P, PluginSnapshot> describe;

    /**
     * @param platformName platform name used in validation messages, for example {@code Bukkit}
     * @param installed lists the currently installed native plugins
     * @param resolve returns the native plugin an owner object stands for, or null when it is not one
     * @param describe converts one native plugin to the shared snapshot contract
     */
    public PlatformPlugins(
            @NotNull String platformName,
            @NotNull Supplier<Collection<P>> installed,
            @NotNull Function<Object, P> resolve,
            @NotNull Function<P, PluginSnapshot> describe) {
        this.platformName = Objects.requireNonNull(platformName, "platformName");
        this.installed = Objects.requireNonNull(installed, "installed");
        this.resolve = Objects.requireNonNull(resolve, "resolve");
        this.describe = Objects.requireNonNull(describe, "describe");
    }

    /** Native plugin that {@code owner} stands for, or null. */
    @Nullable
    public P find(@NotNull Object owner) {
        return resolve.apply(owner);
    }

    /** Metadata of {@code owner}; an unsupported owner is reported through {@code logging} and yields null. */
    @Nullable
    public PluginSnapshot metadata(@NotNull Object owner, @NotNull PlatformLogging logging) {
        P plugin = find(owner);
        return plugin != null ? describe.apply(plugin) : PlatformOwnership.unsupportedOwner(owner, logging);
    }

    /** Metadata of every installed plugin. */
    @NotNull
    public List<PluginSnapshot> snapshots() {
        List<PluginSnapshot> snapshots = new ArrayList<>();
        for (P plugin : installed.get()) {
            snapshots.add(describe.apply(plugin));
        }
        return snapshots;
    }

    /** Installed plugin identifiers mapped to their versions. */
    @NotNull
    public Map<String, String> versions() {
        Map<String, String> versions = new LinkedHashMap<>();
        for (PluginSnapshot snapshot : snapshots()) {
            versions.put(snapshot.getId(), snapshot.getVersion());
        }
        return versions;
    }

    /**
     * Native plugin of an {@code owner} that must be installed on this platform.
     *
     * @param role what the owner is used for; it starts the failure message, for example {@code Remote policy owner}
     * @throws IllegalArgumentException when {@code owner} is not a native plugin or is not installed
     */
    @NotNull
    public P requireInstalled(@NotNull Object owner, @NotNull String role) {
        P plugin = find(owner);
        if (plugin == null) {
            throw new IllegalArgumentException(
                    role + " must be a " + platformName + " plugin; received " + owner.getClass().getName());
        }
        if (!installed.get().contains(plugin)) {
            throw new IllegalArgumentException(
                    role + " is not installed on " + platformName + ": " + owner.getClass().getName());
        }
        return plugin;
    }
}
