package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable metadata for one installed platform plugin. */
public final class PluginSnapshot {
    private final String id;
    private final String name;
    private final String version;
    private final List<String> authors;
    private final String mainClass;

    /** A plugin with no declared authors and no main class. */
    public PluginSnapshot(@NotNull String id, @NotNull String name, @NotNull String version) {
        this(id, name, version, Collections.<String>emptyList(), null);
    }

    /** A plugin with declared authors and no main class. */
    public PluginSnapshot(
            @NotNull String id, @NotNull String name, @NotNull String version, @NotNull List<String> authors) {
        this(id, name, version, authors, null);
    }

    /**
     * @param id plugin identifier
     * @param name display name
     * @param version plugin version
     * @param authors declared authors
     * @param mainClass main class name, when the platform reports one
     */
    public PluginSnapshot(
            @NotNull String id,
            @NotNull String name,
            @NotNull String version,
            @NotNull List<String> authors,
            @Nullable String mainClass) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.authors = authors;
        this.mainClass = mainClass;
    }

    /** Plugin identifier. */
    @NotNull
    public String getId() {
        return id;
    }

    /** Display name. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Plugin version. */
    @NotNull
    public String getVersion() {
        return version;
    }

    /** Declared authors. */
    @NotNull
    public List<String> getAuthors() {
        return authors;
    }

    /** Main class name, when the platform reports one. */
    @Nullable
    public String getMainClass() {
        return mainClass;
    }

    /** Flat map form of this plugin entry, used when rendering diagnostics. */
    @NotNull
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("version", version);
        map.put("authors", authors);
        map.put("mainClass", mainClass);
        return map;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PluginSnapshot)) {
            return false;
        }
        PluginSnapshot that = (PluginSnapshot) other;
        return id.equals(that.id) && name.equals(that.name) && version.equals(that.version)
                && authors.equals(that.authors) && Objects.equals(mainClass, that.mainClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, version, authors, mainClass);
    }

    @Override
    public String toString() {
        return "PluginSnapshot(id=" + id + ", name=" + name + ", version=" + version + ", authors=" + authors
                + ", mainClass=" + mainClass + ")";
    }
}
