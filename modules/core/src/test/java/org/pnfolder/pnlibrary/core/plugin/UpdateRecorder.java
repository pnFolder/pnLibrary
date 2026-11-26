package org.pnfolder.pnlibrary.core.plugin;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.updates.UpdateBackups;
import org.pnfolder.pnlibrary.api.updates.UpdateHistory;
import org.pnfolder.pnlibrary.api.updates.UpdatePlans;
import org.pnfolder.pnlibrary.api.updates.UpdateProducts;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.api.updates.UpdateSettings;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;

/** An update service that keeps the last registration it was given. */
public final class UpdateRecorder implements UpdateService {
    public PluginUpdateRequest request;
    public ProductDescriptor product;
    public List<PluginDependency> dependencies = new ArrayList<>();

    @Override
    public @NotNull UpdateRegistration register(@NotNull Object owner, @NotNull ProductDescriptor product,
                                                @NotNull PluginUpdateRequest request, @NotNull List<PluginDependency> dependencies) {
        this.product = product;
        this.request = request;
        this.dependencies = dependencies;
        return PluginRegistryFixture.emptyProxy(UpdateRegistration.class);
    }

    @Override
    public @NotNull UpdateProducts getProducts() {
        return new UpdateProducts() {
            @Override
            public @NotNull List<UpdateRegistration> all() {
                return new ArrayList<>();
            }

            @Override
            public @Nullable UpdateRegistration get(@NotNull String product) {
                return null;
            }
        };
    }

    @Override
    public @NotNull UpdatePlans getPlans() {
        throw new IllegalStateException("not used");
    }

    @Override
    public @NotNull UpdateSettings getSettings() {
        throw new IllegalStateException("not used");
    }

    @Override
    public @NotNull UpdateBackups getBackups() {
        throw new IllegalStateException("not used");
    }

    @Override
    public @NotNull UpdateHistory getHistory() {
        throw new IllegalStateException("not used");
    }
}
