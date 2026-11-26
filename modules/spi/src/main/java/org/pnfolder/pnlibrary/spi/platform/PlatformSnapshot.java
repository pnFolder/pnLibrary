package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable platform snapshot used by diagnostics and lifecycle messages. */
public final class PlatformSnapshot {
    private final String name;
    private final String version;
    private final String vendor;
    private final Integer onlinePlayers;
    private final List<String> registeredServers;
    private final List<PluginSnapshot> plugins;
    private final JavaSnapshot java;

    /** A snapshot with only a name and a version; the JVM is the running one. */
    public PlatformSnapshot(@NotNull String name, @NotNull String version) {
        this(name, version, null, null, Collections.emptyList(), Collections.emptyList(),
                JavaSnapshot.current());
    }

    /** A snapshot with a vendor; the JVM is the running one. */
    public PlatformSnapshot(@NotNull String name, @NotNull String version, @Nullable String vendor) {
        this(name, version, vendor, null, Collections.emptyList(), Collections.emptyList(),
                JavaSnapshot.current());
    }

    /** A snapshot with players, servers and plugins; the JVM is the running one. */
    public PlatformSnapshot(
            @NotNull String name,
            @NotNull String version,
            @Nullable String vendor,
            @Nullable Integer onlinePlayers,
            @NotNull List<String> registeredServers,
            @NotNull List<PluginSnapshot> plugins) {
        this(name, version, vendor, onlinePlayers, registeredServers, plugins, JavaSnapshot.current());
    }

    /**
     * @param name platform product name
     * @param version platform version string
     * @param vendor platform vendor, when the platform reports one
     * @param onlinePlayers number of online players, or {@code null} when unknown
     * @param registeredServers names of backend servers registered with a proxy
     * @param plugins installed plugins reported by the platform
     * @param java JVM the platform runs on
     */
    public PlatformSnapshot(
            @NotNull String name,
            @NotNull String version,
            @Nullable String vendor,
            @Nullable Integer onlinePlayers,
            @NotNull List<String> registeredServers,
            @NotNull List<PluginSnapshot> plugins,
            @NotNull JavaSnapshot java) {
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.vendor = vendor;
        this.onlinePlayers = onlinePlayers;
        this.registeredServers = registeredServers;
        this.plugins = plugins;
        this.java = java;
    }

    /** Snapshot for a platform whose details cannot be read. */
    @NotNull
    public static PlatformSnapshot unknown(@NotNull String platform) {
        return new PlatformSnapshot(platform, "неизвестна");
    }

    /** Platform product name. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Platform version string. */
    @NotNull
    public String getVersion() {
        return version;
    }

    /** Platform vendor, when the platform reports one. */
    @Nullable
    public String getVendor() {
        return vendor;
    }

    /** Number of online players, or {@code null} when unknown. */
    @Nullable
    public Integer getOnlinePlayers() {
        return onlinePlayers;
    }

    /** Names of backend servers registered with a proxy. */
    @NotNull
    public List<String> getRegisteredServers() {
        return registeredServers;
    }

    /** Installed plugins reported by the platform. */
    @NotNull
    public List<PluginSnapshot> getPlugins() {
        return plugins;
    }

    /** JVM the platform runs on. */
    @NotNull
    public JavaSnapshot getJava() {
        return java;
    }

    /** Flat map form of this snapshot, used when rendering diagnostics. */
    @NotNull
    public Map<String, Object> asMap() {
        List<Map<String, Object>> pluginMaps = new ArrayList<>();
        for (PluginSnapshot plugin : plugins) {
            pluginMaps.add(plugin.asMap());
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("platformName", name);
        map.put("platformVersion", version);
        map.put("platformVendor", vendor);
        map.put("onlinePlayersCount", onlinePlayers);
        map.put("registeredServersCount", registeredServers.size());
        map.put("registeredServerNames", registeredServers);
        map.put("plugins", pluginMaps);
        map.put("java", java);
        return map;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlatformSnapshot)) {
            return false;
        }
        PlatformSnapshot that = (PlatformSnapshot) other;
        return name.equals(that.name) && version.equals(that.version) && Objects.equals(vendor, that.vendor)
                && Objects.equals(onlinePlayers, that.onlinePlayers)
                && registeredServers.equals(that.registeredServers) && plugins.equals(that.plugins)
                && java.equals(that.java);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version, vendor, onlinePlayers, registeredServers, plugins, java);
    }

    @Override
    public String toString() {
        return "PlatformSnapshot(name=" + name + ", version=" + version + ", vendor=" + vendor
                + ", onlinePlayers=" + onlinePlayers + ", registeredServers=" + registeredServers
                + ", plugins=" + plugins + ", java=" + java + ")";
    }
}
