package org.pnfolder.pnlibrary.core.menu.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleControl;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.modules.ModuleLabels;
import org.pnfolder.pnlibrary.core.commands.modules.NamedModule;

/** One module of a plugin as the menu sees it; plain values, so it also crosses to another server. */
public final class ModuleInfo {
    /** What can be done to a module; the words are the ones {@code /pn modules} uses. */
    public static final List<String> ACTIONS =
        Collections.unmodifiableList(Arrays.asList("enable", "disable", "restart", "reload", "auto-on", "auto-off"));

    private final String key;
    private final String plugin;
    private final String title;
    private final ModuleState state;
    private final boolean autoStart;
    private final boolean controllable;
    private final String error;
    private final List<String> libraries;
    private final List<String> pluginLibraries;
    private final String description;

    /**
     * @param libraries the libraries the module registered itself, as {@code group:artifact:version}
     * @param pluginLibraries the libraries its plugin registered for all of its modules
     * @param description what the module is for, as its plugin wrote it; null when it did not
     */
    public ModuleInfo(@NotNull String key, @NotNull String plugin, @NotNull String title, @NotNull ModuleState state,
                      boolean autoStart, boolean controllable, @Nullable String error, @NotNull List<String> libraries,
                      @NotNull List<String> pluginLibraries, @Nullable String description) {
        this.key = key;
        this.plugin = plugin;
        this.title = title;
        this.state = state;
        this.autoStart = autoStart;
        this.controllable = controllable;
        this.error = error;
        this.libraries = libraries;
        this.pluginLibraries = pluginLibraries;
        this.description = description;
    }

    public ModuleInfo(@NotNull String key, @NotNull String plugin, @NotNull String title, @NotNull ModuleState state,
                      boolean autoStart, boolean controllable, @Nullable String error) {
        this(key, plugin, title, state, autoStart, controllable, error, Collections.<String>emptyList(),
            Collections.<String>emptyList(), null);
    }

    public @NotNull String getKey() {
        return key;
    }

    public @NotNull String getPlugin() {
        return plugin;
    }

    public @NotNull String getTitle() {
        return title;
    }

    public @NotNull ModuleState getState() {
        return state;
    }

    public boolean getAutoStart() {
        return autoStart;
    }

    public boolean isControllable() {
        return controllable;
    }

    public @Nullable String getError() {
        return error;
    }

    public @NotNull List<String> getLibraries() {
        return libraries;
    }

    public @NotNull List<String> getPluginLibraries() {
        return pluginLibraries;
    }

    public @Nullable String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ModuleInfo)) {
            return false;
        }
        ModuleInfo that = (ModuleInfo) other;
        return key.equals(that.key) && plugin.equals(that.plugin) && title.equals(that.title) && state == that.state
            && autoStart == that.autoStart && controllable == that.controllable && Objects.equals(error, that.error)
            && libraries.equals(that.libraries) && pluginLibraries.equals(that.pluginLibraries)
            && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, plugin, title, state, autoStart, controllable, error, libraries, pluginLibraries, description);
    }

    @Override
    public String toString() {
        return "ModuleInfo(key=" + key + ", state=" + state + ")";
    }

    /** The view of one module without the libraries of its plugin. */
    public static @NotNull ModuleInfo of(@NotNull String key, @NotNull ModuleContext module) {
        return of(key, module, Collections.<String>emptyList());
    }

    /** The view of one module with the libraries its plugin registered for all of its modules. */
    public static @NotNull ModuleInfo of(@NotNull String key, @NotNull ModuleContext module, @NotNull List<String> pluginLibraries) {
        Throwable failure = module.getControl().getLastError();
        String error = failure == null ? null : failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
        return new ModuleInfo(key, module.getMetadata().getName(), ModuleLabels.title(module), module.getControl().getState(),
            module.getControl().getAutoStart(), module.getControl().isControllable(), error,
            coordinates(module.getLibraries().getLoaded()), pluginLibraries, module.getMetadata().getDescription());
    }

    /** Every module of every registered plugin, ordered by key. */
    public static @NotNull List<ModuleInfo> all(@NotNull PnLibrary library) {
        List<ModuleInfo> modules = new ArrayList<>();
        for (PluginRegistration plugin : library.getPlugins().all()) {
            List<String> shared = coordinates(plugin.getLibraries().getLoaded());
            for (ModuleContext module : plugin.all()) {
                modules.add(of(plugin.getId().getValue() + ":" + module.getId().getValue(), module, shared));
            }
        }
        Collections.sort(modules, new Comparator<ModuleInfo>() {
            @Override
            public int compare(ModuleInfo a, ModuleInfo b) {
                return a.key.compareTo(b.key);
            }
        });
        return modules;
    }

    /** Runs {@code action} on the module {@code key} and returns how it looks afterwards. */
    public static @NotNull ModuleInfo apply(@NotNull PnLibrary library, @NotNull String key, @NotNull String action) {
        ModuleContext module = find(library, key);
        if (!ACTIONS.contains(action)) {
            throw new IllegalArgumentException("Неизвестное действие: " + action);
        }
        ModuleControl control = module.getControl();
        switch (action) {
            case "enable":
                control.enable();
                break;
            case "disable":
                control.disable();
                break;
            case "restart":
                control.restart();
                break;
            case "reload":
                control.reload();
                break;
            case "auto-on":
                control.setAutoStart(true);
                break;
            default:
                control.setAutoStart(false);
                break;
        }
        for (PluginRegistration plugin : library.getPlugins().all()) {
            for (ModuleContext candidate : plugin.all()) {
                if (candidate == module) {
                    return of(key, module, coordinates(plugin.getLibraries().getLoaded()));
                }
            }
        }
        throw new NoSuchElementException("Модуль " + key + " не найден");
    }

    private static ModuleContext find(PnLibrary library, String key) {
        for (NamedModule named : ModuleLabels.named(library)) {
            if (named.getName().equals(key)) {
                return named.getModule();
            }
        }
        throw new IllegalArgumentException("Модуль " + key + " не найден");
    }

    private static List<String> coordinates(Collection<? extends ModuleLibrary> libraries) {
        List<String> coordinates = new ArrayList<>();
        for (ModuleLibrary library : libraries) {
            coordinates.add(library.getCoordinates());
        }
        return coordinates;
    }
}
