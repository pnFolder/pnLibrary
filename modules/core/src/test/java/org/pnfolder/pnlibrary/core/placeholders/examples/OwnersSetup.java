package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.plugin.ModuleContext;

public final class OwnersSetup {
    private OwnersSetup() {
    }

    public static void setupOwners(ModuleContext context) {
        context.getPlaceholders().registerPublished(null, new ShopPrices());
    }
}
