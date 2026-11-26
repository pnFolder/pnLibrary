package org.pnfolder.pnlibrary.currency;

import java.util.Objects;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandOptions;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyPlaceholderOptions;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorage;
import org.pnfolder.pnlibrary.api.currency.ManagedCurrencyBuilder;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Assembles a storage-backed provider and its optional placeholder publications. */
final class ManagedCurrencyDefinition extends CurrencyOptions implements ManagedCurrencyBuilder {
    private final PluginId owner;
    private final String name;
    private final CurrencyDescriptor.Builder descriptor;
    private final ManagedPlaceholderOptions placeholderOptions;
    private final ManagedCommandOptions commandOptions = new ManagedCommandOptions();
    private CurrencyStorage storage;
    private boolean ownsStorage;
    private String service;

    ManagedCurrencyDefinition(@NotNull PluginId owner, @NotNull String name) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = Objects.requireNonNull(name, "name");
        this.descriptor = CurrencyDescriptor.builder(name);
        this.service = owner.getValue();
        this.placeholderOptions = new ManagedPlaceholderOptions(owner.getValue());
    }

    @Override
    @NotNull
    public ManagedCurrencyBuilder descriptor(@NotNull Consumer<CurrencyDescriptor.Builder> configure) {
        configure.accept(descriptor);
        return this;
    }

    @Override
    @NotNull
    public ManagedCurrencyBuilder storage(@NotNull CurrencyStorage storage) {
        this.storage = storage;
        ownsStorage = false;
        return this;
    }

    @Override
    @NotNull
    public ManagedCurrencyBuilder ownedStorage(@NotNull CurrencyStorage storage) {
        this.storage = storage;
        ownsStorage = true;
        return this;
    }

    @Override
    @NotNull
    public ManagedCurrencyBuilder service(@NotNull String name) {
        if (CurrencyNames.isBlank(name)) {
            throw new IllegalArgumentException("Managed currency service must not be blank");
        }
        service = name.trim();
        return this;
    }

    @Override
    @NotNull
    public ManagedCurrencyBuilder placeholders(@NotNull Consumer<CurrencyPlaceholderOptions> configure) {
        configure.accept(placeholderOptions);
        return this;
    }

    @Override
    @NotNull
    public ManagedCurrencyBuilder commands(@NotNull Consumer<CurrencyCommandOptions> configure) {
        configure.accept(commandOptions);
        return this;
    }

    /** Builds the storage-backed provider; fails when no storage was configured. */
    @NotNull
    CurrencyProvider provider() {
        if (storage == null) {
            throw new IllegalStateException("Managed currency " + owner + ":" + name + " requires storage");
        }
        return new ManagedCurrencyProvider(new CurrencyKey(owner, CurrencyNames.normalize(name)), descriptor.build(), storage,
                service, ownsStorage, commandOptions.build());
    }

    /** Registers balance, formatted and symbol placeholders for {@code currency}; the caller closes the returned handles. */
    @NotNull
    List<AutoCloseable> installPlaceholders(@NotNull CurrencyRegistration currency, @NotNull PlaceholderService placeholders) {
        List<AutoCloseable> handles = new ArrayList<>();
        if (!placeholderOptions.isEnabled()) {
            return handles;
        }
        String normalizedName = CurrencyNames.normalize(name);
        handles.add(register(placeholders, normalizedName, "balance", ofPlayer(currency, balance -> balance)));
        handles.add(register(placeholders, normalizedName, "formatted", ofPlayer(currency, currency::format)));
        handles.add(register(placeholders, normalizedName, "symbol",
                playerId -> CompletableFuture.completedFuture(currency.getDescriptor().getSymbol())));
        return handles;
    }

    private AutoCloseable register(PlaceholderService placeholders, String normalizedName, String key,
                                   Function<UUID, CompletionStage<Object>> resolver) {
        return placeholders.value("currency." + normalizedName + "." + key, "Currency " + normalizedName + ": " + key,
                placeholderOptions.isPlaceholderApi() ? placeholderOptions.getNamespace() : null,
                viewer -> resolver.apply(viewer.getPlayerId()));
    }

    private static Function<UUID, CompletionStage<Object>> ofPlayer(CurrencyRegistration currency,
                                                                    Function<BigDecimal, Object> transform) {
        return playerId -> playerId == null ? CompletableFuture.completedFuture(null)
                : currency.balance(playerId).thenApply(transform);
    }
}
