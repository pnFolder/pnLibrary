package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.plugin.ModuleContext;

/** Connecting everything in the {@code onEnable} of a module. Closing the module releases it all. */
public final class ClanSetup {
    private ClanSetup() {
    }

    public static void setupPlaceholders(ModuleContext context) {
        // The converter and the formats belong to this module and go away with it.
        context.getPlaceholders().converter(Clan.class, new ClanByName());
        context.getPlaceholders().formats(new ClanFormats());
        context.getPlaceholders().registerPublished("pnclans", new ClanBasics(), new ClanArguments(), new ClanBank(), new AboutTheViewer());
    }
}
