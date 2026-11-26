package org.pnfolder.pnlibrary.core.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
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
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleControl;
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule;
import org.pnfolder.pnlibrary.api.plugin.lifecycle.PluginLifecycle;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMessages;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionService;
import org.pnfolder.pnlibrary.api.services.ServiceManager;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.text.ComponentService;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.core.commands.engine.CommandFeedbackScope;
import org.pnfolder.pnlibrary.core.exceptions.CleanupStep;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.core.expansions.LocalizationExpansion;
import org.pnfolder.pnlibrary.core.commands.modules.MinecraftForcible;
import org.pnfolder.pnlibrary.core.plugin.module.minecraft.MinecraftGate;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleActionService;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleCommands;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleController;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleDiagnosticsName;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleExpansions;
import org.pnfolder.pnlibrary.core.plugin.module.library.ModuleLibraryLoader;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleLifecycleReporter;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleMessages;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleResources;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;

/** One module of a plugin: its resources, hooks and the state an administrator can change. */
final class LoadedModule implements ModuleContext, MinecraftForcible {
    private final ModuleHost host;
    private final LoadedPlugin parent;
    private final ModuleId id;
    private final PluginId key;
    private final PluginMetadata metadata;
    private final ModuleResources resources;
    private final String productId;
    private final MinecraftGate minecraftGate;
    private final AtomicBoolean contextClosed = new AtomicBoolean(false);
    private final CommandFeedbackScope commandFeedback;
    private final ModuleCommands moduleCommands;
    private final ModuleLibraries libraries;
    private final ModuleController controller;
    private final ModuleExpansions expansions;
    private final PluginLifecycle lifecycle;
    private final PluginMessages messages;
    private final ActionService actions;

    LoadedModule(ModuleHost host, LoadedPlugin parent, ModuleId id, PluginId key, PluginMetadata metadata, ModuleResources resources,
                 boolean placeholderApiEnabled, @Nullable String productId, @Nullable PluginModule module, MinecraftGate gate) {
        this.host = host;
        this.parent = parent;
        this.id = id;
        this.key = key;
        this.metadata = metadata;
        this.resources = resources;
        this.productId = productId;
        this.minecraftGate = gate;
        Object owner = parent.getOwner();
        this.commandFeedback = new CommandFeedbackScope(parent.getCommandFeedback());
        this.moduleCommands = new ModuleCommands(owner, host.commands, commandFeedback, this::isClosed);
        this.libraries = ModuleLibraryLoader.through(key.getValue(), LoadedPlugins.class.getClassLoader(), owner, parent.getLibraries(),
                host.libraryFetcher, host.identity.getType(), host.preferences, parent.getNativeId().getValue() + ":" + id.getValue());
        this.controller = new ModuleController(key.getValue(), module, host.preferences, gate::reason, this::releaseRunningResources);
        this.controller.bind(this);
        this.expansions = new ModuleExpansions(host.shared.getExpansions(), host.shared.getLibraryExpansions());
        this.lifecycle = new ModuleLifecycleReporter(owner, id, metadata, resources, host.logging, host.shared.getPlaceholders(),
                placeholderApiEnabled, this::isClosed);
        this.messages = new ModuleMessages(owner, host.logging);
        this.actions = new ModuleActionService(resources, host.scenarios, ModuleDiagnosticsName.of(key, id));
    }

    private void releaseRunningResources() {
        resources.getTasks().cancelAll();
        List<CommandRegistration> drained = moduleCommands.drain();
        Collections.reverse(drained);
        for (CommandRegistration registration : drained) {
            registration.close();
        }
    }

    ModuleController getController() {
        return controller;
    }

    void startRemotePolicy(RemotePolicySettings policy) {
        host.remotePolicies.start(parent.getOwner(), this, policy, parent::close);
    }

    @Override
    public void forceMinecraft(boolean on) {
        minecraftGate.setForced(on);
    }

    @Override
    public @NotNull MinecraftCompatibility getMinecraft() {
        return minecraftGate.compatibility();
    }

    @Override
    public @NotNull ModuleId getId() {
        return id;
    }

    @Override
    public @NotNull PluginId getKey() {
        return key;
    }

    @Override
    public @NotNull PluginMetadata getMetadata() {
        return metadata;
    }

    @Override
    public @NotNull CommandRegistration registerCommand(@NotNull CommandDefinition command) {
        return moduleCommands.register(command);
    }

    @Override
    public <E extends CommandError> void onCommandError(@NotNull Class<E> type, @NotNull CommandErrorHandler<E> handler) {
        commandFeedback.getHandlers().put(type, handler);
    }

    @Override
    public boolean isClosed() {
        return contextClosed.get();
    }

    @Override
    public @NotNull ModuleLibraries getLibraries() {
        return libraries;
    }

    @Override
    public @NotNull ModuleControl getControl() {
        return controller;
    }

    @Override
    public @NotNull TaskScope getTasks() {
        return resources.getTasks();
    }

    @Override
    public @NotNull EventScope getEvents() {
        return resources.getEvents();
    }

    @Override
    public @NotNull ServiceManager getServices() {
        return resources.getServices();
    }

    @Override
    public @NotNull ExpansionManager getExpansions() {
        return expansions;
    }

    @Override
    public @NotNull MinecraftLocalization getLocalization() {
        return expansions.view(LocalizationExpansion.class);
    }

    @Override
    public @NotNull PnLogger getLogger() {
        return resources.getLogger();
    }

    @Override
    public @NotNull ConfigScope getConfigs() {
        return resources.getConfigs();
    }

    @Override
    public @NotNull PlaceholderService getPlaceholders() {
        return resources.getPlaceholders();
    }

    @Override
    public @NotNull ComponentService getComponents() {
        return resources.getComponents();
    }

    @Override
    public @NotNull CooldownService getCooldowns() {
        return resources.getCooldowns();
    }

    @Override
    public @NotNull MetricsController getMetrics() {
        return resources.getMetrics();
    }

    @Override
    public @NotNull ErrorReporter getErrors() {
        return resources.getErrors();
    }

    @Override
    public @Nullable DiagnosticRegistration getDiagnostics() {
        return resources.getDiagnostics();
    }

    @Override
    public @Nullable UpdateRegistration getUpdates() {
        return resources.getUpdates();
    }

    @Override
    public @Nullable DownloadRegistration getDownloads() {
        return resources.getDownloads();
    }

    @Override
    public @NotNull PluginLifecycle getLifecycle() {
        return lifecycle;
    }

    @Override
    public @NotNull PluginMessages getMessages() {
        return messages;
    }

    @Override
    public @NotNull ActionService getActions() {
        return actions;
    }

    @Override
    public void close() {
        LoadedModule detached = parent.detach(id);
        if (detached != null) {
            detached.closeInternal();
        }
    }

    void closeInternal() {
        if (!contextClosed.compareAndSet(false, true)) {
            return;
        }
        controller.stop();
        if (productId != null) {
            synchronized (host.plugins) {
                host.productDescriptors.remove(productId);
            }
        }
        final List<CommandRegistration> registrations = moduleCommands.drain();
        ResourceCleanup.closeAll(
                () -> closeCommands(registrations),
                expansions::close,
                () -> host.scenarios.removeOwner(ModuleDiagnosticsName.of(key, id)),
                () -> resources.close(host.diagnostics));
    }

    private static void closeCommands(List<CommandRegistration> registrations) {
        List<CleanupStep> steps = new ArrayList<>();
        for (int i = registrations.size() - 1; i >= 0; i--) {
            steps.add(registrations.get(i)::close);
        }
        ResourceCleanup.closeAll(steps);
    }
}
