package org.pnfolder.pnlibrary.spi.libraries;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * A library pnLibrary does not pack and downloads through Libby only when something needs it, such as a database
 * driver or the policy compiler. Nothing is loaded for features a server never uses.
 */
public final class OptionalLibrary {
    private final String id;
    private final String probe;
    private final ClassLoader classLoader;

    /**
     * @param id group name known to the loader: {@code mysql}, {@code mongo} or {@code compiler}
     * @param probe a class of the library, used to tell whether it is already visible
     */
    public OptionalLibrary(@NotNull String id, @NotNull String probe, @NotNull ClassLoader classLoader) {
        this.id = Objects.requireNonNull(id, "id");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    /** Whether the library can be used now, loading it first when it is missing. Never throws; false means it is unavailable. */
    public boolean ensure() {
        return present() || (loadThroughLoader() && present());
    }

    private boolean present() {
        try {
            Class.forName(probe, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    // The loader is plain Java in the same JAR and runs before Kotlin exists, so this module cannot depend on it.
    private boolean loadThroughLoader() {
        try {
            return (Boolean) Class.forName("org.pnfolder.pnlibrary.loader.LibraryLoader", true, classLoader)
                    .getMethod("loadOptional", String.class).invoke(null, id);
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }
}
