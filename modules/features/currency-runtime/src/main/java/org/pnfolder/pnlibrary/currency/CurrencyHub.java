package org.pnfolder.pnlibrary.currency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.Currency;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistrationState;
import org.pnfolder.pnlibrary.api.currency.CurrencyService;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/**
 * Registered currencies with their owners, aliases and visibility; {@link #scope} gives one plugin its own view.
 * A provider that throws is turned into a failed result for the caller and also passed to the report, so it is not lost.
 */
final class CurrencyHub implements CurrencyProviderRegistry, AutoCloseable {
    private final BiConsumer<String, Throwable> report;
    private final ConcurrentHashMap<CurrencyKey, RegisteredCurrency> entries = new ConcurrentHashMap<CurrencyKey, RegisteredCurrency>();
    private final ConcurrentHashMap<CurrencyKey, CurrencyKey> aliases = new ConcurrentHashMap<CurrencyKey, CurrencyKey>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    CurrencyHub() {
        this((message, error) -> { });
    }

    CurrencyHub(@NotNull BiConsumer<String, Throwable> report) {
        this.report = report;
    }

    /** Creates the per-plugin view of this registry; call once per plugin that registers or resolves currencies. */
    @NotNull
    CurrencyService scope(@NotNull PluginId owner, @NotNull PlaceholderService placeholders) {
        return new CurrencyScope(this, owner, placeholders);
    }

    @Override
    @NotNull
    public CurrencyRegistration register(@NotNull PluginId owner, @NotNull String name, @NotNull CurrencyProvider provider) {
        return install(owner, name, provider, Collections.<String>emptySet());
    }

    @Override
    @Nullable
    public Currency get(@NotNull String reference) {
        int separator = reference.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        return active(new CurrencyKey(PluginId.of(reference.substring(0, separator)),
                CurrencyNames.normalize(reference.substring(separator + 1))));
    }

    @Override
    @NotNull
    public List<Currency> all() {
        List<RegisteredCurrency> active = new ArrayList<RegisteredCurrency>();
        for (RegisteredCurrency entry : entries.values()) {
            if (entry.getState() == CurrencyRegistrationState.ACTIVE) {
                active.add(entry);
            }
        }
        Collections.sort(active, Comparator.comparing((RegisteredCurrency entry) -> entry.getKey().toString()));
        return Collections.<Currency>unmodifiableList(active);
    }

    /** The active currency registered under {@code requested} or an alias of it, or {@code null}. */
    @Nullable
    Currency active(@NotNull CurrencyKey requested) {
        CurrencyKey target = aliases.get(requested);
        RegisteredCurrency entry = entries.get(target != null ? target : requested);
        return entry != null && entry.getState() == CurrencyRegistrationState.ACTIVE ? entry : null;
    }

    /** The currency {@code owner} registered under {@code name} or an alias of it, active or not. */
    @Nullable
    RegisteredCurrency ownedBy(@NotNull PluginId owner, @NotNull String name) {
        CurrencyKey requested = new CurrencyKey(owner, CurrencyNames.normalize(name));
        CurrencyKey target = aliases.get(requested);
        RegisteredCurrency entry = entries.get(target != null ? target : requested);
        return entry != null && entry.getKey().getOwner().equals(owner) ? entry : null;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (RegisteredCurrency entry : new ArrayList<RegisteredCurrency>(entries.values())) {
            entry.close();
        }
        entries.clear();
        aliases.clear();
    }

    /** Validates names and aliases, then publishes the entry atomically under the registry lock. */
    @NotNull
    RegisteredCurrency install(@NotNull PluginId owner, @NotNull String name, @NotNull CurrencyProvider provider,
                               @NotNull Set<String> requestedAliases) {
        if (closed.get()) {
            throw new IllegalStateException("Currency registry is closed");
        }
        CurrencyKey key = new CurrencyKey(owner, CurrencyNames.normalize(name));
        Set<String> normalizedAliases = new java.util.LinkedHashSet<String>();
        for (String alias : requestedAliases) {
            normalizedAliases.add(CurrencyNames.normalize(alias));
        }
        RegisteredCurrency entry = new RegisteredCurrency(this, key, provider, normalizedAliases);
        synchronized (entries) {
            if (entries.containsKey(key)) {
                throw new IllegalArgumentException("Currency " + key + " is already registered");
            }
            for (String alias : normalizedAliases) {
                CurrencyKey aliasKey = new CurrencyKey(owner, alias);
                if (aliases.containsKey(aliasKey) || entries.containsKey(aliasKey)) {
                    throw new IllegalArgumentException("Currency alias " + owner + ":" + alias + " is already registered");
                }
            }
            entries.put(key, entry);
            for (String alias : normalizedAliases) {
                aliases.put(new CurrencyKey(owner, alias), key);
            }
        }
        return entry;
    }

    /** Removes {@code entry} and its aliases, unless something else has taken its place. */
    void forget(@NotNull RegisteredCurrency entry, @NotNull Set<String> entryAliases) {
        synchronized (entries) {
            entries.remove(entry.getKey(), entry);
            for (String alias : entryAliases) {
                aliases.remove(new CurrencyKey(entry.getKey().getOwner(), alias), entry.getKey());
            }
        }
    }

    /** Passes a provider failure to the report given at creation. */
    void report(@NotNull String message, @NotNull Throwable error) {
        report.accept(message, error);
    }
}
