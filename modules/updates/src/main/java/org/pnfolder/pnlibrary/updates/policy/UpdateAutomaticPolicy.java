package org.pnfolder.pnlibrary.updates.policy;

import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.updates.service.RegisteredUpdate;

/** Decides whether a resolved plan may be staged without an explicit user action. */
public final class UpdateAutomaticPolicy {
    private final UpdateConfiguration configuration;
    private final UpdatePolicyOverrides overrides;
    private final Supplier<List<RegisteredUpdate>> registrations;

    /** Creates a policy that has no administrator choices. */
    public UpdateAutomaticPolicy(@NotNull UpdateConfiguration configuration,
                                 @NotNull Supplier<List<RegisteredUpdate>> registrations) {
        this(configuration, null, registrations);
    }

    /** Creates a policy whose defaults are overridden by the choices in {@code overrides}. */
    public UpdateAutomaticPolicy(@NotNull UpdateConfiguration configuration,
                                 @Nullable UpdatePolicyOverrides overrides,
                                 @NotNull Supplier<List<RegisteredUpdate>> registrations) {
        this.configuration = configuration;
        this.overrides = overrides;
        this.registrations = registrations;
    }

    /** Whether the plan may be installed without an administrator. */
    public boolean allows(@NotNull UpdatePlanSnapshot snapshot) {
        UpdatePlan plan = snapshot.getPlan();
        if (plan == null) {
            return true;
        }
        for (ProductChange change : plan.getChanges()) {
            if (!allows(change.getProduct())) {
                return false;
            }
        }
        return true;
    }

    private boolean allows(ProductId component) {
        Boolean chosen = overrides == null ? null : overrides.automatic(component.getValue());
        if (component.getValue().equals(PnLibraryIdentity.PRODUCT_ID)) {
            return chosen != null ? chosen : configuration.getLibrary().getAutomaticDownload();
        }
        if (chosen != null) {
            return chosen;
        }
        Boolean allPlugins = overrides == null ? null : overrides.allPlugins();
        if (allPlugins != null) {
            return allPlugins;
        }
        return configuration.getPluginAutomaticDownload() || requestedAutomatic(component);
    }

    private boolean requestedAutomatic(ProductId component) {
        for (RegisteredUpdate registration : registrations.get()) {
            if (registration.getDescriptor().getId().equals(component)) {
                return registration.getRequest().getAutomaticDownload();
            }
        }
        return false;
    }
}
