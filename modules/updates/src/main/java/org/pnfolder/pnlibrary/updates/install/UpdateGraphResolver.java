package org.pnfolder.pnlibrary.updates.install;

import java.util.Objects;
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
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformPluginInventory;
import org.pnfolder.pnlibrary.updates.engine.preferences.FreezeStore;
import org.pnfolder.pnlibrary.updates.engine.resolver.ResolutionResult;
import org.pnfolder.pnlibrary.updates.engine.resolver.ResolverPolicy;
import org.pnfolder.pnlibrary.updates.engine.resolver.UpdateResolver;
import org.pnfolder.pnlibrary.updates.engine.catalog.RefreshMode;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalog;
import org.pnfolder.pnlibrary.updates.engine.catalog.CatalogRelease;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;
import org.pnfolder.pnlibrary.updates.policy.UpdatePolicyOverrides;
import org.pnfolder.pnlibrary.updates.service.registration.RegisteredUpdate;

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
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.freezes = Objects.requireNonNull(freezes, "freezes");
        this.overrides = overrides;
        this.failure = Objects.requireNonNull(failure, "failure");
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
                    Collections.emptyList(),
                    Collections.emptyList()));
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

    /** Looks up only pnLibrary's own catalog for a manual upgrade. */
    @NotNull
    public UpdatePlan forceLibrary(@NotNull List<RegisteredUpdate> registrations) {
        ProductId product = ProductId.of(PnLibraryIdentity.PRODUCT_ID);
        for (RegisteredUpdate registration : registrations) {
            if (product.equals(registration.getDescriptor().getId())) {
                List<ProductRelease> releases = loadReleases(Collections.singletonList(registration), RefreshMode.FORCE_REMOTE);
                UpdateChannel channel = channelFor(registration);
                registration.observe(releases, channel);
                return new UpdateResolver(product).forceLibrary(registration.installedProduct(PnLibraryApi.VERSION),
                        releases, channel, identity.getType(), JavaRuntime.getFeature());
            }
        }
        throw new IllegalStateException("pnLibrary ещё не зарегистрировалась для обновлений.");
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
        return new ResolverPolicy(allowManaged, plugins == null ? Collections.emptySet() : plugins);
    }
}
