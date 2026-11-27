package org.pnfolder.pnlibrary.core.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler;
import org.pnfolder.pnlibrary.api.compat.declaration.MinecraftVersions;
import org.pnfolder.pnlibrary.api.compat.declaration.NewerAction;
import org.pnfolder.pnlibrary.api.compat.declaration.UnsupportedAction;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibraries;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginBuilder;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.core.commands.engine.CommandFeedbackScope;
import org.pnfolder.pnlibrary.core.exceptions.CleanupStep;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.console.ConsoleCard;
import org.pnfolder.pnlibrary.core.plugin.module.minecraft.MinecraftBlockCard;
import org.pnfolder.pnlibrary.core.plugin.module.minecraft.MinecraftNoticeCard;
import org.pnfolder.pnlibrary.core.plugin.module.minecraft.MinecraftGate;
import org.pnfolder.pnlibrary.core.plugin.module.minecraft.MinecraftSupportRule;
import org.pnfolder.pnlibrary.core.plugin.module.definition.ModuleDefinitionBuilder;
import org.pnfolder.pnlibrary.core.plugin.module.library.ModuleLibraryLoader;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleResources;
import org.pnfolder.pnlibrary.core.plugin.module.ModuleServiceKey;

/** One native plugin registered in the library; it owns the contexts of its modules. */
final class LoadedPlugin implements PluginRegistration {
    private final ModuleHost host;
    private final Object owner;
    private final PluginId nativeId;
    private final long registrationId;
    private final Map<ModuleId, LoadedModule> moduleContexts = new LinkedHashMap<>();
    private final MinecraftSupportRule minecraftRule = new MinecraftSupportRule();
    private final ModuleLibraries libraries;
    private final AtomicBoolean pluginClosed = new AtomicBoolean(false);
    private final CommandFeedbackScope commandFeedback = new CommandFeedbackScope();

    LoadedPlugin(ModuleHost host, Object owner, PluginId nativeId, long registrationId) {
        this.host = host;
        this.owner = owner;
        this.nativeId = nativeId;
        this.registrationId = registrationId;
        this.libraries = ModuleLibraryLoader.through("p-" + nativeId.getValue() + "-" + registrationId,
                LoadedPlugins.class.getClassLoader(), owner, null, host.libraryFetcher, host.identity.getType(),
                host.preferences, nativeId.getValue());
    }

    PluginId getNativeId() {
        return nativeId;
    }

    /** What the whole plugin answers to a denied or mistyped command; each module's scope inherits from it. */
    CommandFeedbackScope getCommandFeedback() {
        return commandFeedback;
    }

    MinecraftSupportRule getMinecraftRule() {
        return minecraftRule;
    }

    @Override
    public @NotNull Object getOwner() {
        return owner;
    }

    @Override
    public @NotNull PluginId getId() {
        return nativeId;
    }

    @Override
    public @NotNull ModuleLibraries getLibraries() {
        return libraries;
    }

    @Override
    public boolean isClosed() {
        return pluginClosed.get();
    }

    @Override
    public void supportedMinecraft(@NotNull MinecraftVersions versions) {
        minecraftRule.add(versions);
    }

    @Override
    public void onUnsupportedMinecraft(@NotNull UnsupportedAction action) {
        minecraftRule.setUnsupportedAction(action);
    }

    @Override
    public void onNewerMinecraft(@NotNull NewerAction action) {
        minecraftRule.setNewerAction(action);
    }

    @Override
    public void allowForce(boolean allowed) {
        minecraftRule.setForceAllowed(allowed);
    }

    @Override
    public void allowForceNewer(boolean allowed) {
        minecraftRule.setForceNewerAllowed(allowed);
    }

    @Override
    public <E extends CommandError> void onCommandError(@NotNull Class<E> type, @NotNull CommandErrorHandler<E> handler) {
        commandFeedback.getHandlers().put(type, handler);
    }

    @Override
    public @NotNull ModuleContext registerModule(@NotNull ModuleId id, @NotNull Consumer<PluginBuilder> configure) {
        return start(id, null, configure);
    }

    @Override
    public @NotNull ModuleContext registerModule(@NotNull ModuleId id, @NotNull PluginModule module,
                                                 @NotNull Consumer<PluginBuilder> configure) {
        return start(id, module, configure);
    }

    private ModuleContext start(ModuleId id, @Nullable PluginModule module, Consumer<PluginBuilder> configure) {
        LoadedModule context = register(id, module, configure);
        context.getController().start();
        return context;
    }

    private LoadedModule register(ModuleId id, @Nullable PluginModule module, Consumer<PluginBuilder> configure) {
        synchronized (host.plugins) {
            synchronized (moduleContexts) {
                checkOpenFor(id);
                ModuleDefinitionBuilder definition = new ModuleDefinitionBuilder();
                configure.accept(definition);
                ProductDescriptor descriptor = host.productResolver.resolve(owner, id, definition);
                if (descriptor != null && host.productDescriptors.containsKey(descriptor.getId().getValue())) {
                    throw new IllegalArgumentException("Component " + descriptor.getId() + " is already registered");
                }
                definition.bindUpdatesTo(descriptor);
                host.dependencyValidator.validate(definition.getDependencies(), host.productDescriptors);
                MinecraftGate gate = openGate(id, definition, module != null);
                return attach(id, definition, descriptor, module, gate);
            }
        }
    }

    private void checkOpenFor(ModuleId id) {
        if (pluginClosed.get()) {
            throw new IllegalStateException("Plugin context is closed");
        }
        if (host.closed.get()) {
            throw new IllegalStateException("PluginRegistry is closed");
        }
        if (moduleContexts.containsKey(id)) {
            throw new IllegalArgumentException("Module " + id + " is already registered for this plugin");
        }
    }

    private MinecraftGate openGate(ModuleId id, ModuleDefinitionBuilder definition, boolean controllable) {
        String scope = nativeId.getValue() + ":" + id.getValue();
        MinecraftGate gate = MinecraftGate.open(scope, host.preferences, definition.getMinecraftRule(), minecraftRule,
                host.identity.getMinecraftVersion());
        boolean fails = gate.mustFail(controllable);
        if (gate.reason() != null && host.console != null) {
            MinecraftBlockCard.of(id.getValue(), host.ownership.ownerDetails(owner), gate, fails)
                .send(line -> host.console.console(owner, line));
        }
        if (fails) {
            throw new IllegalStateException(gate.reason());
        }
        return gate;
    }

    private LoadedModule attach(ModuleId id, ModuleDefinitionBuilder definition, @Nullable ProductDescriptor descriptor,
                                @Nullable PluginModule module, MinecraftGate gate) {
        PluginId serviceKey = ModuleServiceKey.of(registrationId, nativeId, id);
        PluginMetadata metadata = host.metadataFactory.create(owner, id, definition);
        ModuleResources resources = host.resourceFactory.create(owner, id, serviceKey, nativeId, definition, descriptor);
        LoadedModule context = new LoadedModule(host, this, id, serviceKey, metadata, resources, definition.isPlaceholderApiEnabled(),
                descriptor == null ? null : descriptor.getId().getValue(), module, gate);
        moduleContexts.put(id, context);
        if (descriptor != null) {
            host.productDescriptors.put(descriptor.getId().getValue(), descriptor);
        }
        try {
            ConsoleCard notice = MinecraftNoticeCard.of(id.getValue(), host.ownership.ownerDetails(owner), gate);
            if (notice != null && host.console != null) {
                notice.send(line -> host.console.console(owner, line));
            }
            loadDeclaredLibraries(context, definition);
            if (definition.getRemotePolicy() != null) {
                context.startRemotePolicy(definition.getRemotePolicy());
            }
        } catch (Throwable error) {
            moduleContexts.remove(id);
            context.closeInternal();
            throw error;
        }
        return context;
    }

    /** Loads the libraries the module declared in its builder; an optional one that fails only warns. */
    private void loadDeclaredLibraries(LoadedModule context, ModuleDefinitionBuilder definition) {
        for (Map.Entry<ModuleLibrary, BooleanSupplier> declared : definition.getLibraries().entrySet()) {
            ModuleLibrary library = declared.getKey();
            if (!declared.getValue().getAsBoolean()) {
                continue;
            }
            try {
                context.getLibraries().load(library);
            } catch (IllegalStateException failure) {
                if (library.isRequired()) {
                    throw failure;
                }
                context.getLogger().warning("Optional library " + library.getCoordinates() + " is not loaded: " + failure.getMessage());
            }
        }
    }

    @Override
    public @Nullable ModuleContext getModule(@NotNull ModuleId id) {
        synchronized (moduleContexts) {
            return moduleContexts.get(id);
        }
    }

    @Override
    public void unregisterModule(@NotNull ModuleId id) {
        LoadedModule module = detach(id);
        if (module != null) {
            module.closeInternal();
        }
    }

    @Override
    public @NotNull List<ModuleContext> all() {
        synchronized (moduleContexts) {
            return Collections.unmodifiableList(new ArrayList<>(moduleContexts.values()));
        }
    }

    @Override
    public void close() {
        LoadedPlugin plugin = host.detachPlugin(owner);
        if (plugin != null) {
            plugin.closeInternal();
        }
    }

    LoadedModule detach(ModuleId id) {
        synchronized (moduleContexts) {
            return moduleContexts.remove(id);
        }
    }

    void closeInternal() {
        if (!pluginClosed.compareAndSet(false, true)) {
            return;
        }
        List<LoadedModule> current;
        synchronized (moduleContexts) {
            current = new ArrayList<>(moduleContexts.values());
            moduleContexts.clear();
        }
        Collections.reverse(current);
        List<CleanupStep> steps = new ArrayList<>();
        for (final LoadedModule module : current) {
            steps.add(module::closeInternal);
        }
        steps.add(() -> {
            if (host.commands != null) {
                host.commands.unregisterOwner(owner);
            }
        });
        ResourceCleanup.closeAll(steps);
    }
}
