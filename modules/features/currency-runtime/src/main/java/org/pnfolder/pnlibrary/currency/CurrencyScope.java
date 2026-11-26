package org.pnfolder.pnlibrary.currency;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.Currency;
import org.pnfolder.pnlibrary.api.currency.CurrencyDefinitionBuilder;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistrationOptions;
import org.pnfolder.pnlibrary.api.currency.CurrencyService;
import org.pnfolder.pnlibrary.api.currency.ManagedCurrencyBuilder;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Registry view of one plugin: its own registrations plus the currencies whose access policy admits it. */
final class CurrencyScope implements CurrencyService {
    private final CurrencyHub hub;
    private final PluginId owner;
    private final PlaceholderService placeholders;
    private final Set<CurrencyKey> owned = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean scopeClosed = new AtomicBoolean(false);

    CurrencyScope(@NotNull CurrencyHub hub, @NotNull PluginId owner, @NotNull PlaceholderService placeholders) {
        this.hub = Objects.requireNonNull(hub, "hub");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
    }

    @Override
    @NotNull
    public CurrencyRegistration register(@NotNull String name, @NotNull Consumer<CurrencyDefinitionBuilder> configure) {
        checkOpen();
        CurrencyDefinition builder = new CurrencyDefinition(name);
        configure.accept(builder);
        return own(hub.install(owner, name, builder.provider(), builder.getAliases()));
    }

    @Override
    @NotNull
    public CurrencyRegistration managed(@NotNull String name, @NotNull Consumer<ManagedCurrencyBuilder> configure) {
        checkOpen();
        ManagedCurrencyDefinition builder = new ManagedCurrencyDefinition(owner, name);
        configure.accept(builder);
        RegisteredCurrency registration = own(hub.install(owner, name, builder.provider(), builder.getAliases()));
        try {
            for (AutoCloseable handle : builder.installPlaceholders(registration, placeholders)) {
                registration.attach(handle);
            }
        } catch (RuntimeException | Error error) {
            registration.close();
            throw error;
        }
        return registration;
    }

    @Override
    @NotNull
    public CurrencyRegistration register(@NotNull String name, @NotNull CurrencyProvider provider,
                                         @NotNull Consumer<CurrencyRegistrationOptions> configure) {
        checkOpen();
        CurrencyOptions options = new CurrencyOptions();
        configure.accept(options);
        return own(hub.install(owner, name, provider, options.getAliases()));
    }

    @Override
    @Nullable
    public Currency get(@NotNull String reference) {
        return hub.active(parseReference(reference));
    }

    @Override
    @NotNull
    public List<Currency> all() {
        return hub.all();
    }

    @Override
    public void unregister(@NotNull String name) {
        RegisteredCurrency entry = hub.ownedBy(owner, name);
        if (entry != null) {
            entry.close();
        }
    }

    @Override
    public void close() {
        if (!scopeClosed.compareAndSet(false, true)) {
            return;
        }
        for (CurrencyKey key : new ArrayList<>(owned)) {
            RegisteredCurrency entry = hub.ownedBy(key.getOwner(), key.getName());
            if (entry != null) {
                entry.close();
            }
        }
        owned.clear();
    }

    private RegisteredCurrency own(RegisteredCurrency registration) {
        owned.add(registration.getKey());
        return registration;
    }

    private void checkOpen() {
        if (scopeClosed.get()) {
            throw new IllegalStateException("Currencies for " + owner + " are closed");
        }
    }

    /** Resolves {@code owner:name} or a bare name, which belongs to this scope's plugin. */
    private CurrencyKey parseReference(String reference) {
        int separator = reference.indexOf(':');
        if (separator < 0) {
            return new CurrencyKey(owner, CurrencyNames.normalize(reference));
        }
        return new CurrencyKey(PluginId.of(reference.substring(0, separator)),
                CurrencyNames.normalize(reference.substring(separator + 1)));
    }
}
