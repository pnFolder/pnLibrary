package org.pnfolder.pnlibrary.api.plugin.control;

import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Switches one module on and off. The same operations are behind {@code /pn modules} and the module screens of the
 * menu.
 *
 * <p>A module registered without a {@link PluginModule} has nothing to switch: {@link #getState()} stays
 * {@link ModuleState#ENABLED} and every change fails with {@link UnsupportedOperationException}.
 */
public interface ModuleControl {
    /** Current state. */
    @NotNull
    ModuleState getState();

    /** The error that put the module into {@link ModuleState#FAILED}, or {@code null}. */
    @Nullable
    Throwable getLastError();

    /** Whether the module has hooks, that is, was registered with a {@link PluginModule}. */
    boolean isControllable();

    /** Whether the module starts when the server starts; an administrator's choice is saved and survives restarts. */
    boolean getAutoStart();

    /** Switches the module on. Does nothing when it already works. */
    @NotNull
    CompletionStage<ModuleState> enable();

    /** Switches the module off for now; it starts again with the server unless {@link #getAutoStart()} is off. */
    @NotNull
    CompletionStage<ModuleState> disable();

    /** Disables the module and enables it again. */
    @NotNull
    CompletionStage<ModuleState> restart();

    /** Re-reads the module's settings; when the module cannot do that in place it is restarted. */
    @NotNull
    CompletionStage<ModuleState> reload();

    /** Saves whether the module starts with the server. This does not switch it on or off right now. */
    void setAutoStart(boolean enabled);
}
