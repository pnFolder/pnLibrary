package org.pnfolder.pnlibrary.core.commands.modules;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.spi.libraries.ExternalLibraries;

/**
 * One library registered by a plugin or a module, as the menu and {@code /pn modules libs} show it; plain values, so it
 * also crosses to another server. Fields are named as they are written on the link.
 *
 * <p>{@code owner} is {@code plugin} when the whole plugin registered it, {@code plugin:module} for one module;
 * {@code trust} is {@link #GIVEN} when the author gave a sum the jar is checked against, {@link #NONE} otherwise;
 * {@code state} is {@code LOADED}, {@code DISABLED}, {@code FAILED} or {@code SKIPPED}; {@code scope} is {@code PLUGIN},
 * {@code ISOLATED} or {@code LIBRARY}.
 */
public final class LibraryRow {
    public static final String GIVEN = "GIVEN";
    public static final String NONE = "NONE";

    private final String owner;
    private final String title;
    private final String coordinates;
    private final String version;
    private final String description;
    private final List<String> repositories;
    private final boolean isolated;
    private final boolean required;
    private final boolean verified;
    private final boolean conflict;
    private final String file;
    private final long size;
    private final String trust;
    private final String scope;
    private final String place;
    private final String state;
    private final boolean autoLoad;
    private final String error;
    private final boolean restartRequired;

    public LibraryRow(@NotNull String owner, @NotNull String title, @NotNull String coordinates, @NotNull String version,
            @Nullable String description, @NotNull List<String> repositories, boolean isolated, boolean required,
            boolean verified, boolean conflict, @Nullable String file, long size, @NotNull String trust,
            @NotNull String scope, @Nullable String place, @NotNull String state, boolean autoLoad, @Nullable String error,
            boolean restartRequired) {
        this.owner = owner;
        this.title = title;
        this.coordinates = coordinates;
        this.version = version;
        this.description = description;
        this.repositories = repositories;
        this.isolated = isolated;
        this.required = required;
        this.verified = verified;
        this.conflict = conflict;
        this.file = file;
        this.size = size;
        this.trust = trust;
        this.scope = scope;
        this.place = place;
        this.state = state;
        this.autoLoad = autoLoad;
        this.error = error;
        this.restartRequired = restartRequired;
    }

    /** A row with no file, place or error, loaded and automatic. */
    public LibraryRow(@NotNull String owner, @NotNull String title, @NotNull String coordinates, @NotNull String version,
            @Nullable String description, @NotNull List<String> repositories, boolean isolated, boolean required,
            boolean verified, boolean conflict) {
        this(owner, title, coordinates, version, description, repositories, isolated, required, verified, conflict,
                null, -1, NONE, "PLUGIN", null, "LOADED", true, null, false);
    }

    @NotNull
    public String getOwner() {
        return owner;
    }

    @NotNull
    public String getTitle() {
        return title;
    }

    @NotNull
    public String getCoordinates() {
        return coordinates;
    }

    @NotNull
    public String getVersion() {
        return version;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @NotNull
    public List<String> getRepositories() {
        return repositories;
    }

    public boolean getIsolated() {
        return isolated;
    }

    public boolean getRequired() {
        return required;
    }

    public boolean getVerified() {
        return verified;
    }

    /** Another owner uses a different version of the same library. */
    public boolean getConflict() {
        return conflict;
    }

    /** Where the jar lies on this server, or {@code null} while it is not known. */
    @Nullable
    public String getFile() {
        return file;
    }

    /** Bytes of that jar, or -1 when unknown. */
    public long getSize() {
        return size;
    }

    @NotNull
    public String getTrust() {
        return trust;
    }

    @NotNull
    public String getScope() {
        return scope;
    }

    /** The same place counted from the server folder ({@code plugins/pnLibrary/libraries/...}), for people to read. */
    @Nullable
    public String getPlace() {
        return place;
    }

    @NotNull
    public String getState() {
        return state;
    }

    public boolean getAutoLoad() {
        return autoLoad;
    }

    @Nullable
    public String getError() {
        return error;
    }

    /** Switched off while in use: it goes away at the next restart. */
    public boolean getRestartRequired() {
        return restartRequired;
    }

    /** The plugin that owns the library, also when a module registered it. */
    @NotNull
    public String getPlugin() {
        int colon = owner.indexOf(':');
        return colon < 0 ? owner : owner.substring(0, colon);
    }

    /** The module that registered the library, or {@code null} when the whole plugin did. */
    @Nullable
    public String getModule() {
        int colon = owner.indexOf(':');
        return colon < 0 || colon == owner.length() - 1 ? null : owner.substring(colon + 1);
    }

    /** {@code плагину shop} or {@code модулю rewards плагина shop}: whose the library is, in words. */
    @NotNull
    public String getOwnerText() {
        String module = getModule();
        return module == null ? "плагину " + getPlugin() : "модулю " + module + " плагина " + getPlugin();
    }

    /** The folder of the jar counted from the server folder, without the file name. */
    @Nullable
    public String getFolder() {
        if (place == null) {
            return null;
        }
        int slash = place.lastIndexOf('/');
        return slash <= 0 ? null : place.substring(0, slash);
    }

    /** The file name of the jar. */
    @Nullable
    public String getFileName() {
        return place == null ? null : place.substring(place.lastIndexOf('/') + 1);
    }

    /** The same row with {@code place} as its place. */
    @NotNull
    public LibraryRow withPlace(@Nullable String place) {
        return new LibraryRow(owner, title, coordinates, version, description, repositories, isolated, required, verified,
                conflict, file, size, trust, scope, place, state, autoLoad, error, restartRequired);
    }

    /** Whether this row belongs to {@code name}: the plugin itself, or any of its modules, or exactly that module. */
    public boolean belongsTo(@NotNull String name) {
        return owner.equalsIgnoreCase(name) || owner.regionMatches(true, 0, name + ":", 0, name.length() + 1);
    }

    private static String locateLoaded(String coordinates) {
        return ExternalLibraries.fileOf(LibraryRow.class.getClassLoader(), coordinates);
    }

    /** {@code file} counted from the server folder with {@code /}, or as it is when it lies elsewhere. */
    @NotNull
    public static String placeOf(@NotNull String file, @Nullable Path root) {
        Path path = Paths.get(file).toAbsolutePath().normalize();
        Path inside = root == null ? null : root.toAbsolutePath().normalize();
        if (inside != null && !path.startsWith(inside)) {
            inside = null;
        }
        return (inside == null ? path : inside.relativize(path)).toString().replace('\\', '/');
    }

    /** {@link #placeOf(String, Path)} counted from the server folder that the runtime knows. */
    @NotNull
    public static String placeOf(@NotNull String file) {
        Path plugins = DiskUsage.getPluginsFolder();
        return placeOf(file, plugins == null ? null : plugins.getParent());
    }

    /** Every library of every plugin and module of this server. */
    @NotNull
    public static List<LibraryRow> all(@NotNull PnLibrary library) {
        return LibraryScan.all(library, LibraryRow::locateLoaded);
    }

    /** Every library of every plugin and module of this server; {@code locate} finds the jar of a library. */
    @NotNull
    public static List<LibraryRow> all(@NotNull PnLibrary library, @NotNull Function<String, String> locate) {
        return LibraryScan.all(library, locate);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LibraryRow)) {
            return false;
        }
        LibraryRow that = (LibraryRow) other;
        return isolated == that.isolated && required == that.required && verified == that.verified
                && conflict == that.conflict && size == that.size && autoLoad == that.autoLoad
                && restartRequired == that.restartRequired && owner.equals(that.owner) && title.equals(that.title)
                && coordinates.equals(that.coordinates) && version.equals(that.version)
                && Objects.equals(description, that.description) && repositories.equals(that.repositories)
                && Objects.equals(file, that.file) && trust.equals(that.trust) && scope.equals(that.scope)
                && Objects.equals(place, that.place) && state.equals(that.state) && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, title, coordinates, version, description, repositories, isolated, required, verified,
                conflict, file, size, trust, scope, place, state, autoLoad, error, restartRequired);
    }

    @Override
    public String toString() {
        return "LibraryRow(owner=" + owner + ", title=" + title + ", coordinates=" + coordinates + ", version=" + version
                + ", state=" + state + ")";
    }
}
