package org.pnfolder.pnlibrary.api.plugin;

import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler;
import org.pnfolder.pnlibrary.api.compat.MinecraftCompatibility;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.events.bus.EventScope;
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibraries;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.metrics.MetricsController;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleControl;
import org.pnfolder.pnlibrary.api.plugin.lifecycle.PluginLifecycle;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMessages;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionService;
import org.pnfolder.pnlibrary.api.services.ServiceManager;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.text.ComponentService;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;

/** Owns the isolated pnLibrary capabilities of one logical plugin module. */
@ApiStatus.NonExtendable
public interface ModuleContext extends AutoCloseable {
    /** Registers a command released when this module closes. */
    @NotNull
    CommandRegistration registerCommand(@NotNull CommandDefinition command);

    /**
     * Builds a command whose lifecycle belongs to this module and registers it:
     * {@code context.registerCommand("shop", c -> c.executes(ctx -> { }))}.
     */
    @NotNull
    default CommandRegistration registerCommand(@NotNull String name, @NotNull Consumer<CommandBuilder> configure) {
        return registerCommand(CommandDefinition.command(name, configure));
    }

    /**
     * Answers errors of class {@code type} for every command of this module with {@code handler}. The most specific
     * level wins: a handler on a command or subcommand over this one, this one over the plugin-wide answer of
     * {@link PluginRegistration#onCommandError}, over the library's standard answer from the library's settings.
     */
    <E extends CommandError> void onCommandError(@NotNull Class<E> type, @NotNull CommandErrorHandler<E> handler);

    /** Answers errors of class {@code type} for every command of this module with the plain {@code message}. */
    default <E extends CommandError> void onCommandError(@NotNull Class<E> type, @NotNull String message) {
        final String text = message.trim();
        onCommandError(type, new CommandErrorHandler<E>() {
            @Override
            public void handle(@NotNull org.pnfolder.pnlibrary.api.commands.CommandContext context, @NotNull E error) {
                context.getSender().sendMessage(Component.text(error.format(text)));
            }
        });
    }

    /** Registry-wide stable identity used by shared services and diagnostics. */
    @NotNull
    PluginId getKey();

    /** Local identity unique within the owning platform plugin. */
    @NotNull
    ModuleId getId();

    /** Immutable platform and display metadata for this module. */
    @NotNull
    PluginMetadata getMetadata();

    /** Buffered lifecycle-message factory. */
    @NotNull
    PluginLifecycle getLifecycle();

    /** Buffered arbitrary-message factory. */
    @NotNull
    PluginMessages getMessages();

    /** Owner-bound event subscriptions. */
    @NotNull
    EventScope getEvents();

    /** Owner-bound scheduled tasks. */
    @NotNull
    TaskScope getTasks();

    /** Module-local service registry. */
    @NotNull
    ServiceManager getServices();

    /**
     * Official Minecraft translations, the only way a plugin reaches them. Loading is shared by every plugin, so a
     * version and locale one plugin loaded is cached for all. Closing this view does nothing, and keyboard layouts
     * registered through its {@code layouts} are removed when this module closes; no handle needs closing.
     *
     * <p>Backed by the library's localization expansion, which is made for game servers.
     */
    @NotNull
    MinecraftLocalization getLocalization();

    /**
     * The library's expansions with ownership: what this module registers through it is disabled and unregistered when
     * the module closes, with no handle to keep. Lookups read the same registry as {@code PnLibrary.expansions}, and
     * ids stay unique across the library.
     */
    @NotNull
    ExpansionManager getExpansions();

    /** Module-bound structured logger. */
    @NotNull
    PnLogger getLogger();

    /** Module-bound configuration scope. */
    @NotNull
    ConfigScope getConfigs();

    /** Portable configurable-action service. */
    @NotNull
    ActionService getActions();

    /** The placeholders of this module: annotated classes in, text out. Everything registered is released with it. */
    @NotNull
    PlaceholderService getPlaceholders();

    /** Adventure component parsing and rendering service. */
    @NotNull
    ComponentService getComponents();

    /** Module-local cooldown service. */
    @NotNull
    CooldownService getCooldowns();

    /** Runtime metrics control for this module. */
    @NotNull
    MetricsController getMetrics();

    /** Normalized error capture shared by local diagnostics and remote providers. */
    @NotNull
    ErrorReporter getErrors();

    /** Diagnostics registration, or {@code null} when diagnostics were not configured. */
    @Nullable
    DiagnosticRegistration getDiagnostics();

    /** Update registration, or {@code null} when updates were not configured. */
    @Nullable
    UpdateRegistration getUpdates();

    /** Auxiliary download registration, or {@code null} when none was configured. */
    @Nullable
    DownloadRegistration getDownloads();

    /** How the server's Minecraft version stands against what this module (or its plugin) declared. */
    @NotNull
    default MinecraftCompatibility getMinecraft() {
        return MinecraftCompatibility.NOT_DECLARED;
    }

    /** The Maven libraries of this module; see {@code PluginModule.libraries()}. */
    @NotNull
    ModuleLibraries getLibraries();

    /** Switches this module on and off; the same operations as {@code /pn modules} and the menu. */
    @NotNull
    ModuleControl getControl();

    /** Whether this module has released all owned resources. */
    boolean isClosed();

    /** Releases every capability and registration owned by this module. */
    @Override
    void close();
}
