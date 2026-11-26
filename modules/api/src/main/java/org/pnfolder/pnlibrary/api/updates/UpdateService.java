package org.pnfolder.pnlibrary.api.updates;

import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;

/**
 * The entry to plugin updates: register a product, then use one of five focused parts.
 *
 * <table>
 * <caption>Parts of the service</caption>
 * <tr><td>{@link #getProducts()}</td><td>which products are registered, and their state</td></tr>
 * <tr><td>{@link #getPlans()}</td><td>checking for releases and staging the resulting plan</td></tr>
 * <tr><td>{@link #getSettings()}</td><td>per-product choices: channel, automatic download, hold, Java build</td></tr>
 * <tr><td>{@link #getBackups()}</td><td>saved rollback sets, rolling back, restoring databases</td></tr>
 * <tr><td>{@link #getHistory()}</td><td>the durable log of what happened</td></tr>
 * </table>
 *
 * <p>Every part is always present and every method works: there are no optional operations. Registration and lookup
 * are safe from any thread; checks are serialized by the runtime and concurrent checks share the one that is running.
 * Completion stages finish on the update executor, so callers must hand platform changes over to their platform or
 * task API. Passing an actor records who asked; it is audit information and grants no permission.
 */
public interface UpdateService {
    /** Registers {@code owner}'s {@code product} with no dependencies and starts watching its releases at once. */
    @NotNull
    default UpdateRegistration register(@NotNull Object owner, @NotNull ProductDescriptor product,
                                        @NotNull PluginUpdateRequest request) {
        return register(owner, product, request, Collections.emptyList());
    }

    /** Registers {@code owner}'s {@code product} and starts watching its releases at once. */
    @NotNull
    UpdateRegistration register(@NotNull Object owner, @NotNull ProductDescriptor product,
                                @NotNull PluginUpdateRequest request, @NotNull List<PluginDependency> dependencies);

    /** Registered products: list, find, state. */
    @NotNull
    UpdateProducts getProducts();

    /** Release checks and update plans. */
    @NotNull
    UpdatePlans getPlans();

    /** Per-product update choices. */
    @NotNull
    UpdateSettings getSettings();

    /** Saved rollback sets and returning to them. */
    @NotNull
    UpdateBackups getBackups();

    /** What happened, newest first, kept across restarts. */
    @NotNull
    UpdateHistory getHistory();
}
