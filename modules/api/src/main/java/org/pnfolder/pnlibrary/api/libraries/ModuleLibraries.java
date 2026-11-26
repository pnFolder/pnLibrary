package org.pnfolder.pnlibrary.api.libraries;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryStatus;

import java.util.List;

/**
 * The libraries of one module, or of a whole plugin ({@code PluginRegistration.libraries}).
 *
 * <p>A module lists its libraries in {@code PluginModule.libraries()}; they are downloaded and verified before
 * {@code onEnable} runs. A plugin may register libraries on itself too: every module of the plugin sees them as
 * {@link #getAvailable() available}. Either may also ask for one later with {@link #load}. The library that fails to
 * load stops the module (state {@code FAILED}) unless it was declared optional.
 */
@ApiStatus.NonExtendable
public interface ModuleLibraries {
    /**
     * Class loader of the {@link LibraryScope#ISOLATED} libraries registered here; its classes are not visible to the
     * plugin's own code, so load their classes through this loader or find them with a {@code ServiceLoader} that uses
     * it. A library declared {@link LibraryScope#PLUGIN} is not here. {@link #loadClass} is easier: it also looks in
     * the plugin's libraries.
     */
    @NotNull
    ClassLoader getClassLoader();

    /** The libraries registered here, not counting the ones a plugin gives its modules. */
    @NotNull
    List<ModuleLibrary> getLoaded();

    /** The libraries this module can use: its own and the plugin's. For a plugin it is the same as {@link #getLoaded()}. */
    @NotNull
    default List<ModuleLibrary> getAvailable() {
        return getLoaded();
    }

    /**
     * Every library registered here with where it stands, also the switched off and the failed ones;
     * {@link #getLoaded()} lists only the loaded.
     */
    @NotNull
    List<LibraryStatus> getStatuses();

    /** Where {@code library} stands, or {@code null} when it was never registered here. */
    @Nullable
    LibraryStatus status(@NotNull ModuleLibrary library);

    /**
     * Chooses whether {@code library} is loaded with the server, and saves the choice. Switching it on loads it now;
     * switching it off keeps it from loading again, and a library already in use stays until the server restarts
     * ({@link LibraryStatus#isRestartRequired()}).
     */
    @NotNull
    LibraryStatus setAutoLoad(@NotNull ModuleLibrary library, boolean enabled);

    /** Tries to load a failed library again; a switched off or loaded one is left as it is. Never throws: the reason is in the status. */
    @NotNull
    LibraryStatus retry(@NotNull ModuleLibrary library);

    /** Downloads, verifies and loads {@code library} unless it is loaded already. Throws {@link IllegalStateException} with the reason when it fails. */
    void load(@NotNull ModuleLibrary library);

    /** Loads a library given as {@code group:artifact:version} with default settings. */
    default void load(@NotNull String coordinates) {
        load(ModuleLibrary.of(coordinates));
    }

    /** Whether {@code library} is loaded here or, for a module, by its plugin. */
    default boolean isLoaded(@NotNull ModuleLibrary library) {
        return getAvailable().contains(library);
    }

    /** The available library with this {@code group} and {@code artifact}, whatever its version, or {@code null}. */
    @Nullable
    default ModuleLibrary find(@NotNull String group, @NotNull String artifact) {
        for (ModuleLibrary library : getAvailable()) {
            if (library.getGroup().equals(group) && library.getArtifact().equals(artifact)) {
                return library;
            }
        }
        return null;
    }

    /**
     * Finds the class {@code name} in the libraries registered here, then, for a module, in the plugin's. Throws
     * {@link ClassNotFoundException} when no library has it.
     */
    @NotNull
    Class<?> loadClass(@NotNull String name) throws ClassNotFoundException;

    /**
     * Takes {@code library} off the list, so it is not shown any more and is not loaded again when a module starts.
     * Java cannot unload a jar that is already in use: its classes stay available until the server restarts. Returns
     * whether it was on the list.
     */
    boolean remove(@NotNull ModuleLibrary library);
}
