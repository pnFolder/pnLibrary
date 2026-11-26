package org.pnfolder.pnlibrary.core.commands.modules;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * What one plugin takes on the disk of this server, split by kind; plain values, so it also crosses to another server.
 * Fields are named as they are written on the link, so renaming one breaks mixed-version networks.
 */
public final class DiskRow {
    private final String name;
    private final long jar;
    private final long configs;
    private final long libraries;
    private final long logs;
    private final long data;

    /**
     * @param name plugin name as its folder or jar is called
     * @param jar the plugin's own jar
     * @param configs settings files in its folder
     * @param libraries jars it downloaded into a {@code lib}, {@code libs} or {@code libraries} folder
     * @param logs log files in its folder
     * @param data everything else in its folder: databases, caches, saved players
     */
    public DiskRow(@NotNull String name, long jar, long configs, long libraries, long logs, long data) {
        this.name = Objects.requireNonNull(name, "name");
        this.jar = jar;
        this.configs = configs;
        this.libraries = libraries;
        this.logs = logs;
        this.data = data;
    }

    @NotNull
    public String getName() {
        return name;
    }

    public long getJar() {
        return jar;
    }

    public long getConfigs() {
        return configs;
    }

    public long getLibraries() {
        return libraries;
    }

    public long getLogs() {
        return logs;
    }

    public long getData() {
        return data;
    }

    public long getTotal() {
        return jar + configs + libraries + logs + data;
    }

    /** The same row with {@code size} more bytes of jar. */
    @NotNull
    public DiskRow withJarAdded(long size) {
        return new DiskRow(name, jar + size, configs, libraries, logs, data);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiskRow)) {
            return false;
        }
        DiskRow that = (DiskRow) other;
        return jar == that.jar && configs == that.configs && libraries == that.libraries && logs == that.logs
                && data == that.data && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, jar, configs, libraries, logs, data);
    }

    @Override
    public String toString() {
        return "DiskRow(name=" + name + ", jar=" + jar + ", configs=" + configs + ", libraries=" + libraries
                + ", logs=" + logs + ", data=" + data + ")";
    }
}
