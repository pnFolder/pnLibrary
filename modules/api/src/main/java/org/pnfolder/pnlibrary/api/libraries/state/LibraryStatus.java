package org.pnfolder.pnlibrary.api.libraries.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;

import java.util.Objects;

/** What one library is doing now and what an administrator chose for it. */
public final class LibraryStatus {
    private final ModuleLibrary library;
    private final LibraryState state;
    private final boolean autoLoad;
    private final String error;
    private final boolean restartRequired;

    /** A status without an error that needs no restart. */
    public LibraryStatus(@NotNull ModuleLibrary library, @NotNull LibraryState state, boolean autoLoad) {
        this(library, state, autoLoad, null, false);
    }

    /**
     * Creates a status.
     *
     * @param library the library
     * @param state where it stands
     * @param autoLoad whether it is loaded with the server; an administrator's choice, saved across restarts
     * @param error why the last load failed, or {@code null}
     * @param restartRequired it was switched off while loaded: Java cannot unload a jar in use, so it goes away at the next restart
     */
    public LibraryStatus(@NotNull ModuleLibrary library, @NotNull LibraryState state, boolean autoLoad,
                         @Nullable String error, boolean restartRequired) {
        this.library = Objects.requireNonNull(library);
        this.state = Objects.requireNonNull(state);
        this.autoLoad = autoLoad;
        this.error = error;
        this.restartRequired = restartRequired;
    }

    /** The library. */
    @NotNull
    public ModuleLibrary getLibrary() {
        return library;
    }

    /** Where it stands. */
    @NotNull
    public LibraryState getState() {
        return state;
    }

    /** Whether it is loaded with the server. */
    public boolean isAutoLoad() {
        return autoLoad;
    }

    /** Why the last load failed, or {@code null}. */
    @Nullable
    public String getError() {
        return error;
    }

    /** Whether it goes away only at the next restart. */
    public boolean isRestartRequired() {
        return restartRequired;
    }

    /** Returns this status with {@code state} replaced. */
    @NotNull
    public LibraryStatus withState(@NotNull LibraryState state) {
        return new LibraryStatus(library, state, autoLoad, error, restartRequired);
    }

    /** Returns this status with {@code autoLoad} replaced. */
    @NotNull
    public LibraryStatus withAutoLoad(boolean autoLoad) {
        return new LibraryStatus(library, state, autoLoad, error, restartRequired);
    }

    /** Returns this status with {@code error} replaced. */
    @NotNull
    public LibraryStatus withError(@Nullable String error) {
        return new LibraryStatus(library, state, autoLoad, error, restartRequired);
    }

    /** Returns this status with {@code restartRequired} replaced. */
    @NotNull
    public LibraryStatus withRestartRequired(boolean restartRequired) {
        return new LibraryStatus(library, state, autoLoad, error, restartRequired);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LibraryStatus)) {
            return false;
        }
        LibraryStatus that = (LibraryStatus) other;
        return autoLoad == that.autoLoad && restartRequired == that.restartRequired && library.equals(that.library)
                && state == that.state && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(library, state, autoLoad, error, restartRequired);
    }

    @NotNull
    @Override
    public String toString() {
        return "LibraryStatus(library=" + library + ", state=" + state + ", autoLoad=" + autoLoad + ", error=" + error
                + ", restartRequired=" + restartRequired + ")";
    }
}
