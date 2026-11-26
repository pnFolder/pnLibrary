package org.pnfolder.pnlibrary.updates.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.InstalledProduct;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.version.PnLibraryApi;
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.PlatformPluginInventory;
import org.pnfolder.pnlibrary.updates.engine.FreezeStore;
import org.pnfolder.pnlibrary.updates.engine.ResolutionResult;
import org.pnfolder.pnlibrary.updates.engine.ResolverPolicy;
import org.pnfolder.pnlibrary.updates.engine.UpdateResolver;
import org.pnfolder.pnlibrary.updates.engine.catalog.RefreshMode;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalog;
import org.pnfolder.pnlibrary.updates.engine.catalog.CatalogRelease;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;
import org.pnfolder.pnlibrary.updates.policy.UpdatePolicyOverrides;
import org.pnfolder.pnlibrary.updates.service.RegisteredUpdate;

/** Loads release catalogs and resolves one coherent update plan for all registered products. */
public final class UpdateGraphResolver {
    private final PlatformPluginInventory inventory;
    private final PlatformIdentity identity;
    private final UpdateConfiguration configuration;
    private final ReleaseCatalogGateway catalogs;
    private final FreezeStore freezes;
    private final UpdatePolicyOverrides overrides;
    private final BiConsumer<String, Throwable> failure;

    /** Creates a resolver; {@code overrides} may be null and {@code failure} receives non-fatal problems. */
    public UpdateGraphResolver(@NotNull PlatformPluginInventory inventory, @NotNull PlatformIdentity identity,
                               @NotNull UpdateConfiguration configuration, @NotNull ReleaseCatalogGateway catalogs,
                               @NotNull FreezeStore freezes, @Nullable UpdatePolicyOverrides overrides,
                               @NotNull BiConsumer<String, Throwable> failure) {
        this.inventory = inventory;
        this.identity = identity;
        this.configuration = configuration;
        this.catalogs = catalogs;
        this.freezes = freezes;
        this.overrides = overrides;
        this.failure = failure;
    }

    /** Creates a resolver that drops non-fatal problems. */
    public UpdateGraphResolver(@NotNull PlatformPluginInventory inventory, @NotNull PlatformIdentity identity,
                               @NotNull UpdateConfiguration configuration, @NotNull ReleaseCatalogGateway catalogs,
                               @NotNull FreezeStore freezes, @Nullable UpdatePolicyOverrides overrides) {
        this(inventory, identity, configuration, catalogs, freezes, overrides, (message, error) -> { });
    }

    /** Creates a resolver without administrator overrides. */
    public UpdateGraphResolver(@NotNull PlatformPluginInventory inventory, @NotNull PlatformIdentity identity,
                               @NotNull UpdateConfiguration configuration, @NotNull ReleaseCatalogGateway catalogs,
                               @NotNull FreezeStore freezes) {
        this(inventory, identity, configuration, catalogs, freezes, null);
    }

    /** Resolves one plan for {@code registrations} from cached catalogs; an empty list gives an empty plan. */
    @NotNull
    public ResolutionResult resolve(@NotNull List<RegisteredUpdate> registrations) {
        return resolve(registrations, RefreshMode.CACHED);
    }

    /** Resolves one plan for {@code registrations}; an empty list gives an empty plan. */
    @NotNull
    public ResolutionResult resolve(@NotNull List<RegisteredUpdate> registrations, @NotNull RefreshMode refreshMode) {
        if (registrations.isEmpty()) {
            return new ResolutionResult.Ready(new UpdatePlan(PnLibraryApi.VERSION,
                    Collections.<ProductChange>emptyList(),
                    Collections.<ProductRelease>emptyList()));
        }
        List<ProductRelease> releases = loadReleases(registrations, refreshMode);
        observeLatestVersions(registrations, releases);

        Map<ProductId, UpdateChannel> channels = new HashMap<>();
        for (RegisteredUpdate registration : registrations) {
            channels.put(registration.getDescriptor().getId(), channelFor(registration));
        }
        return new UpdateResolver(ProductId.of(PnLibraryIdentity.PRODUCT_ID)).resolve(
                installedProducts(registrations), releases, channels, UpdateChannel.STABLE, frozenProducts(),
                identity.getType(), JavaRuntime.getFeature(), resolverPolicy());
    }

    /** The channel of {@code registration}: an administrator's override, the library's own setting, or what the plugin asked for. */
    @NotNull
    public UpdateChannel channelFor(@NotNull RegisteredUpdate registration) {
        return channelFor(registration.getDescriptor().getId(), registration.getRequest().getChannel());
    }

    /** The channel of {@code product}, or {@code fallback} when nothing overrides it. */
    @NotNull
    public UpdateChannel channelFor(@NotNull ProductId product, @NotNull UpdateChannel fallback) {
        UpdateChannel chosen = overrides == null ? null : overrides.channel(product.getValue());
        if (chosen != null) {
            return chosen;
        }
        return product.getValue().equals(PnLibraryIdentity.PRODUCT_ID) ? configuration.getLibrary().getChannel() : fallback;
    }

    private List<InstalledProduct> installedProducts(List<RegisteredUpdate> registrations) {
        List<InstalledProduct> installed = new ArrayList<>();
        for (RegisteredUpdate registration : registrations) {
            boolean library = registration.getDescriptor().getId().getValue().equals(PnLibraryIdentity.PRODUCT_ID);
            installed.add(registration.installedProduct(library ? Integer.valueOf(PnLibraryApi.VERSION) : null));
        }
        return installed;
    }

    private List<ProductRelease> loadReleases(List<RegisteredUpdate> registrations, RefreshMode refreshMode) {
        List<ProductRelease> releases = new ArrayList<>();
        for (RegisteredUpdate registration : registrations) {
            ReleaseCatalog catalog = catalogs.load(registration.getRequest().getCatalog(), refreshMode);
            if (!catalog.getProduct().equalsIgnoreCase(registration.getProduct())) {
                throw new IllegalArgumentException("Release catalog product " + catalog.getProduct()
                        + " does not match " + registration.getDescriptor().getId());
            }
            for (CatalogRelease release : catalog.getReleases()) {
                releases.add(registration.releaseFrom(release));
            }
        }
        return releases;
    }

    private void observeLatestVersions(List<RegisteredUpdate> registrations, List<ProductRelease> releases) {
        for (RegisteredUpdate registration : registrations) {
            List<ProductRelease> productReleases = new ArrayList<>();
            for (ProductRelease release : releases) {
                if (release.getProduct().equals(registration.getDescriptor().getId())) {
                    productReleases.add(release);
                }
            }
            registration.observe(productReleases, channelFor(registration));
        }
    }

    private Set<ProductId> frozenProducts() {
        return new HashSet<>(freezes.active().keySet());
    }

    private ResolverPolicy resolverPolicy() {
        boolean allowManaged = configuration.getDownloads().getAllowManagedPlugins()
                && configuration.getInstallation().getAllowNewPlugins();
        // An unreadable inventory makes external plugin dependencies count as missing,
        // so releases that need them are skipped instead of installed blindly.
        Attempt<Set<String>> installed = Failures.runCatchingNonFatal(() -> inventory.installedPlugins().keySet());
        installed.onFailure(error -> failure.accept(
                "Could not read the installed plugins; releases that need other plugins are skipped", error));
        Set<String> plugins = installed.getOrNull();
        return new ResolverPolicy(allowManaged, plugins == null ? Collections.<String>emptySet() : plugins);
    }
}
