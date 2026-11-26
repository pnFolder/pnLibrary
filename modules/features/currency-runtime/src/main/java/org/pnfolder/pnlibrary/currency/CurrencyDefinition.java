package org.pnfolder.pnlibrary.currency;

import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyDefinitionBuilder;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyOperations;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;

/** Assembles a lightweight callback-backed provider. */
final class CurrencyDefinition extends CurrencyOptions implements CurrencyDefinitionBuilder {
    private final String name;
    private final CurrencyDescriptor.Builder descriptor;
    private final CurrencyOperationsBuilder operations = new CurrencyOperationsBuilder();

    CurrencyDefinition(@NotNull String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.descriptor = CurrencyDescriptor.builder(name);
    }

    @Override
    @NotNull
    public CurrencyDefinitionBuilder descriptor(@NotNull Consumer<CurrencyDescriptor.Builder> configure) {
        configure.accept(descriptor);
        return this;
    }

    @Override
    @NotNull
    public CurrencyDefinitionBuilder operations(@NotNull Consumer<CurrencyOperations> configure) {
        configure.accept(operations);
        return this;
    }

    /** Builds the callback-backed provider; call after the user's configuration has run. */
    @NotNull
    CurrencyProvider provider() {
        return operations.build(descriptor.build(), name);
    }
}
