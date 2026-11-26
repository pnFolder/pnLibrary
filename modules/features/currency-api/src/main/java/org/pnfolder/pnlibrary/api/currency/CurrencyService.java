package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Plugin-owned facade for registering and discovering currencies.
 *
 * <p>Unqualified references resolve inside the owning plugin. Cross-plugin references use canonical
 * {@code owner:name} form. Closing the service closes every registration created through it.
 */
public interface CurrencyService extends AutoCloseable {
    /** Defines and registers a lightweight currency in one call. */
    @NotNull
    CurrencyRegistration register(@NotNull String name, @NotNull Consumer<CurrencyDefinitionBuilder> configure);

    /** Registers a storage-backed currency with atomic transactions and queryable history. */
    @NotNull
    CurrencyRegistration managed(@NotNull String name, @NotNull Consumer<ManagedCurrencyBuilder> configure);

    /** Registers a reusable provider class. */
    @NotNull
    default CurrencyRegistration register(@NotNull String name, @NotNull CurrencyProvider provider) {
        return register(name, provider, options -> { });
    }

    /** Registers a reusable {@code provider} with aliases or an explicit access policy. */
    @NotNull
    CurrencyRegistration register(@NotNull String name, @NotNull CurrencyProvider provider,
                                  @NotNull Consumer<CurrencyRegistrationOptions> configure);

    /** Resolves an accessible local name, alias, or canonical {@code owner:name} reference. */
    @Nullable
    Currency get(@NotNull String reference);

    /** Resolves {@code reference} or fails when missing, disabled, closed, or inaccessible. */
    @NotNull
    default Currency require(@NotNull String reference) {
        Currency currency = get(reference);
        if (currency == null) {
            throw new IllegalStateException("Currency " + reference + " is unavailable or inaccessible");
        }
        return currency;
    }

    /** Returns all active currencies visible to this plugin in canonical-key order. */
    @NotNull
    List<Currency> all();

    /** Closes a currency owned by this plugin, addressed by name or alias. */
    void unregister(@NotNull String name);

    /** Closes every registration owned by this service. Safe to repeat. */
    @Override
    void close();
}
