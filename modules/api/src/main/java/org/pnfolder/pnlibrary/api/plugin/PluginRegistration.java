package org.pnfolder.pnlibrary.api.plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler;
import org.pnfolder.pnlibrary.api.compat.declaration.MinecraftSupportDeclaration;
import org.pnfolder.pnlibrary.api.config.ConfigInfo;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibraries;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule;

/** Registration of one native platform plugin and all logical modules it owns. */
@ApiStatus.NonExtendable
public interface PluginRegistration extends AutoCloseable, MinecraftSupportDeclaration {
    /** Native platform plugin owning this registration. */
    @NotNull
    Object getOwner();

    /** Identifier of the plugin, as in {@code quests/main:quests.yml}. */
    @NotNull
    PluginId getId();

    /** Whether this registration has released its modules. */
    boolean isClosed();

    /**
     * Answers errors of class {@code type} for every command of this plugin, in all of its modules, with
     * {@code handler}. The most specific level wins: a handler on a command or subcommand over this one, over the
     * library's standard answer from the library's settings.
     */
    <E extends CommandError> void onCommandError(@NotNull Class<E> type, @NotNull CommandErrorHandler<E> handler);

    /** Answers errors of class {@code type} for every command of this plugin with the plain {@code message}. */
    default <E extends CommandError> void onCommandError(@NotNull Class<E> type, @NotNull String message) {
        final String text = message.trim();
        onCommandError(type, new CommandErrorHandler<E>() {
            @Override
            public void handle(@NotNull CommandContext context, @NotNull E error) {
                context.getSender().sendMessage(Component.text(error.format(text)));
            }
        });
    }

    /**
     * The libraries of the whole plugin: what is registered here is available to every module of it, so a library
     * several modules need is downloaded once. Modules keep their own in {@code ModuleContext.libraries}.
     */
    @NotNull
    ModuleLibraries getLibraries();

    /** Every library of this plugin: its own and those of each module, without repeats. */
    @NotNull
    default List<ModuleLibrary> getAllLibraries() {
        Set<ModuleLibrary> libraries = new LinkedHashSet<ModuleLibrary>(getLibraries().getLoaded());
        for (ModuleContext module : all()) {
            libraries.addAll(module.getLibraries().getLoaded());
        }
        return new ArrayList<ModuleLibrary>(libraries);
    }

    /** Registers a logical module identified by {@code id} with no extra setup. */
    @NotNull
    default ModuleContext registerModule(@NotNull ModuleId id) {
        return registerModule(id, builder -> {
            });
    }

    /** Registers a logical module identified by {@code id}, set up by {@code configure}. */
    @NotNull
    ModuleContext registerModule(@NotNull ModuleId id, @NotNull Consumer<PluginBuilder> configure);

    /** Registers a module that the library can switch, with no extra setup; see the longer form. */
    @NotNull
    default ModuleContext registerModule(@NotNull ModuleId id, @NotNull PluginModule module) {
        return registerModule(id, module, builder -> {
            });
    }

    /**
     * Registers a module that the library can switch: {@code module}'s {@code onEnable} runs right away (unless an
     * administrator switched this module off for good), and later {@code /pn modules} and the menu call its hooks.
     */
    @NotNull
    ModuleContext registerModule(@NotNull ModuleId id, @NotNull PluginModule module,
                                 @NotNull Consumer<PluginBuilder> configure);

    /** Normalizes {@code id} and registers a switchable module with no extra setup; see the longer form. */
    @NotNull
    default ModuleContext registerModule(@NotNull String id, @NotNull PluginModule module) {
        return registerModule(ModuleId.of(id), module);
    }

    /** Normalizes {@code id} and registers a switchable module; see the longer form. */
    @NotNull
    default ModuleContext registerModule(@NotNull String id, @NotNull PluginModule module,
                                         @NotNull Consumer<PluginBuilder> configure) {
        return registerModule(ModuleId.of(id), module, configure);
    }

    /** Normalizes {@code id} and registers a logical module with no extra setup. */
    @NotNull
    default ModuleContext registerModule(@NotNull String id) {
        return registerModule(ModuleId.of(id));
    }

    /** Normalizes {@code id} and registers a logical module. */
    @NotNull
    default ModuleContext registerModule(@NotNull String id, @NotNull Consumer<PluginBuilder> configure) {
        return registerModule(ModuleId.of(id), configure);
    }

    /** Returns the module identified by {@code id}, or {@code null} when absent. */
    @Nullable
    ModuleContext getModule(@NotNull ModuleId id);

    /** Normalizes {@code id} and returns the matching module, when present. */
    @Nullable
    default ModuleContext getModule(@NotNull String id) {
        return getModule(ModuleId.of(id));
    }

    /** Returns the module identified by {@code id} or fails when absent. */
    @NotNull
    default ModuleContext requireModule(@NotNull ModuleId id) {
        ModuleContext module = getModule(id);
        if (module == null) {
            throw new IllegalStateException("Module " + id + " is not registered for this plugin");
        }
        return module;
    }

    /** Normalizes {@code id} and returns the matching module or fails. */
    @NotNull
    default ModuleContext requireModule(@NotNull String id) {
        return requireModule(ModuleId.of(id));
    }

    /** Closes and unregisters the module identified by {@code id}. */
    void unregisterModule(@NotNull ModuleId id);

    /** Normalizes {@code id}, then closes and unregisters the matching module. */
    default void unregisterModule(@NotNull String id) {
        unregisterModule(ModuleId.of(id));
    }

    /** Returns an immutable snapshot of logical modules owned by this plugin. */
    @NotNull
    List<ModuleContext> all();

    /** Every configuration file of every module of this plugin. */
    @NotNull
    default List<ConfigInfo> configs() {
        List<ConfigInfo> configs = new ArrayList<ConfigInfo>();
        for (ModuleContext module : all()) {
            configs.addAll(module.getConfigs().list());
        }
        return configs;
    }

    /** Closes every owned module and releases this registration. */
    @Override
    void close();
}
