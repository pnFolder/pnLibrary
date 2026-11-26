package org.pnfolder.pnlibrary.currency;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistrationOptions;

/** Common alias configuration shared by all currency builders. */
class CurrencyOptions implements CurrencyRegistrationOptions {
    private final Set<String> aliases = new LinkedHashSet<String>();

    /** Extra names under which the same currency can be resolved. */
    @NotNull
    Set<String> getAliases() {
        return aliases;
    }

    @Override
    @NotNull
    public CurrencyOptions aliases(@NotNull String... names) {
        java.util.Collections.addAll(aliases, names);
        return this;
    }
}
