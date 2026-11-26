package org.pnfolder.pnlibrary.spi.libraries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Loads a library a module asked for through the platform's Libby loader, which this module cannot depend on: the loader
 * is plain Java in the same JAR, so it is called by name, like {@link OptionalLibrary} does.
 */
public final class ExternalLibraries {
    private static final String LOADER = "org.pnfolder.pnlibrary.loader.LibraryLoader";

    private ExternalLibraries() {
    }

    /** Same as the full overload with no plugin. */
    @Nullable
    public static ClassLoader load(
            @NotNull ClassLoader classLoader,
            @NotNull String owner,
            @NotNull String group,
            @NotNull String artifact,
            @NotNull String version,
            @Nullable String classifier,
            @Nullable String sha256,
            @Nullable String url,
            @NotNull List<String> repositories,
            boolean isolated) {
        return load(classLoader, owner, group, artifact, version, classifier, sha256, url, repositories, isolated, null);
    }

    /**
     * Downloads and loads one library and returns the class loader that holds it, or {@code null} when it joined pnLibrary's own
     * loader ({@code isolated} false).
     *
     * @throws IllegalStateException with the reason when it cannot be loaded
     */
    @Nullable
    public static ClassLoader load(
            @NotNull ClassLoader classLoader,
            @NotNull String owner,
            @NotNull String group,
            @NotNull String artifact,
            @NotNull String version,
            @Nullable String classifier,
            @Nullable String sha256,
            @Nullable String url,
            @NotNull List<String> repositories,
            boolean isolated,
            @Nullable Object plugin) {
        try {
            return (ClassLoader) Class.forName(LOADER, true, classLoader)
                    .getMethod("loadExternal", String.class, String.class, String.class, String.class, String.class,
                            String.class, String.class, String[].class, boolean.class, Object.class)
                    .invoke(null, owner, group, artifact, version, classifier, sha256, url,
                            repositories.toArray(new String[0]), isolated, plugin);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getTargetException();
            String message = cause.getMessage();
            throw new IllegalStateException(message != null ? message : "Library cannot be loaded", cause);
        } catch (ReflectiveOperationException unavailable) {
            throw new IllegalStateException("The library loader is not available on this server");
        }
    }

    /** The jar file of a library this loader has loaded, as a path, or {@code null} when it is not known (never loaded, or no loader). */
    @Nullable
    public static String fileOf(@NotNull ClassLoader classLoader, @NotNull String coordinates) {
        try {
            return (String) Class.forName(LOADER, true, classLoader)
                    .getMethod("fileOf", String.class).invoke(null, coordinates);
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }
}
