package org.pnfolder.pnlibrary.core.updates.catalog;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;

/** Small helpers of the update commands. */
public final class UpdateCatalogSupport {
    private UpdateCatalogSupport() {
    }

    /** The update actor that stands for this sender in the update history. */
    public static @NotNull UpdateActor actorOf(@NotNull CommandSender sender) {
        return new UpdateActor(AudienceExtensions.isConsole(sender) ? UpdateActor.Kind.CONSOLE : UpdateActor.Kind.PLAYER,
            AudienceExtensions.id(sender), AudienceExtensions.name(sender));
    }

    /** The names of all plugins registered for updates, for tab completion. */
    public static @NotNull List<String> productNames(@NotNull PnLibrary library) {
        List<String> names = new ArrayList<>();
        for (UpdateRegistration product : library.getUpdates().getProducts().all()) {
            names.add(product.getSnapshot().getProduct());
        }
        return names;
    }
}
