package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.registry.Visibility;

/** Names known only while running: one placeholder per currency, per setting, per world. No class is needed. */
public final class DynamicPlaceholders {
    private DynamicPlaceholders() {
    }

    public static List<AutoCloseable> register(PlaceholderService placeholders, List<String> currencies) {
        List<AutoCloseable> handles = new ArrayList<>();
        for (final String name : currencies) {
            // {wallet.gold}; with papi set it is also %pn_wallet_gold%. The returned handle removes it.
            handles.add(placeholders.value("wallet." + name, "Balance in " + name, "pn", Visibility.ALL, viewer -> balanceOf(viewer, name)));
        }
        return handles;
    }

    private static Long balanceOf(ViewerInfo viewer, String currency) {
        return viewer.getPlayerId() == null ? null : currency.length() * 100L;
    }
}
