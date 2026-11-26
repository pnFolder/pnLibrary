package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;

/** An error of the plugin's own: fields instead of a map of strings, and its text built from them. */
final class NoMoneyError extends CommandError {
    private final int price;

    NoMoneyError(int price) {
        this.price = price;
    }

    int getPrice() {
        return price;
    }

    @NotNull
    @Override
    public Map<String, Object> placeholders() {
        return Collections.<String, Object>singletonMap("price", price);
    }
}
