package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleControl;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState;
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * Runs the hooks of one {@link PluginModule} and keeps its state. Changes run one at a time; a hook that throws leaves the
 * module {@link ModuleState#FAILED} and the error is kept instead of being thrown at the caller.
 */
public final class ModuleController implements ModuleControl {
    private final String key;
    private final PluginModule module;
    private final ModulePreferences preferences;
    private final Supplier<String> gate;
    private final Runnable release;
    private ModuleContext context;
    private volatile ModuleState state = ModuleState.ENABLED;
    private volatile Throwable lastError;

    /**
     * @param gate why the Minecraft version check keeps the module off, or {@code null}
     * @param release takes back what the library gave the module while it ran (tasks, commands)
     */
    public ModuleController(@NotNull String key, @Nullable PluginModule module, @NotNull ModulePreferences preferences,
                            @NotNull Supplier<String> gate, @NotNull Runnable release) {
        this.key = Objects.requireNonNull(key, "key");
        this.module = module;
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.release = Objects.requireNonNull(release, "release");
    }

    public ModuleController(@NotNull String key, @Nullable PluginModule module, @NotNull ModulePreferences preferences,
                            @NotNull Runnable release) {
        this(key, module, preferences, () -> null, release);
    }

    @Override
    public @NotNull ModuleState getState() {
        return state;
    }

    @Override
    public @Nullable Throwable getLastError() {
        return lastError;
    }

    @Override
    public boolean isControllable() {
        return module != null;
    }

    @Override
    public boolean getAutoStart() {
        return preferences.autoStart(key);
    }

    public void bind(@NotNull ModuleContext context) {
        this.context = context;
    }

    /** First start at registration: stays off when an administrator turned the module off for good. */
    public void start() {
        if (module == null || blocked()) {
            return;
        }
        if (!getAutoStart()) {
            state = ModuleState.DISABLED;
        } else {
            runEnable();
        }
    }

    /** Last stop when the module or its plugin goes away; errors are kept, never thrown. */
    public void stop() {
        if (module != null && state == ModuleState.ENABLED) {
            runDisable();
        }
    }

    /** The Minecraft version check keeps the module off: it stays disabled and the reason is kept as its error. */
    private boolean blocked() {
        String reason = gate.get();
        if (reason == null) {
            return false;
        }
        lastError = new IllegalStateException(reason);
        state = ModuleState.DISABLED;
        return true;
    }

    @Override
    public @NotNull CompletionStage<ModuleState> enable() {
        return change(() -> {
            if (!blocked() && state != ModuleState.ENABLED) {
                runEnable();
            }
        });
    }

    @Override
    public @NotNull CompletionStage<ModuleState> disable() {
        return change(() -> {
            if (state == ModuleState.ENABLED) {
                runDisable();
            } else if (state == ModuleState.FAILED) {
                release.run();
                state = ModuleState.DISABLED;
            }
        });
    }

    @Override
    public @NotNull CompletionStage<ModuleState> restart() {
        return change(() -> {
            if (state == ModuleState.ENABLED) {
                runDisable();
            }
            if (!blocked()) {
                runEnable();
            }
        });
    }

    @Override
    public @NotNull CompletionStage<ModuleState> reload() {
        return change(() -> {
            if (state != ModuleState.ENABLED) {
                return;
            }
            boolean inPlace;
            try {
                inPlace = module.onReload(context);
            } catch (Throwable failure) {
                Failures.rethrowIfFatal(failure);
                error(failure);
                return;
            }
            if (!inPlace) {
                runDisable();
                runEnable();
            }
        });
    }

    @Override
    public void setAutoStart(boolean enabled) {
        requireControllable();
        preferences.setAutoStart(key, enabled);
    }

    private CompletionStage<ModuleState> change(Runnable action) {
        requireControllable();
        synchronized (this) {
            lastError = null;
            action.run();
        }
        return CompletableFuture.completedFuture(state);
    }

    private void requireControllable() {
        if (module == null) {
            throw new UnsupportedOperationException("This module has no enable and disable hooks");
        }
    }

    private void runEnable() {
        try {
            loadLibraries();
            module.onEnable(context);
            state = ModuleState.ENABLED;
        } catch (Throwable failure) {
            Failures.rethrowIfFatal(failure);
            error(failure);
        }
    }

    private void loadLibraries() {
        for (ModuleLibrary library : module.libraries()) {
            try {
                context.getLibraries().load(library);
            } catch (Exception failure) {
                if (library.isRequired()) {
                    throw failure;
                }
                context.getLogger().warning("Optional library " + library + " was not loaded: " + failure.getMessage());
            }
        }
    }

    private void runDisable() {
        try {
            try {
                module.onDisable(context);
            } finally {
                release.run();
            }
            state = ModuleState.DISABLED;
        } catch (Throwable failure) {
            Failures.rethrowIfFatal(failure);
            error(failure);
        }
    }

    private void error(Throwable failure) {
        lastError = failure;
        state = ModuleState.FAILED;
        try {
            context.getLogger().error("Module " + key + " failed: " + failure.getMessage(), failure);
        } catch (Throwable ignored) {
            Failures.rethrowIfFatal(ignored);
        }
    }
}
