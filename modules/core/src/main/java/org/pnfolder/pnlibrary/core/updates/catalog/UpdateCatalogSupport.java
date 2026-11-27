package org.pnfolder.pnlibrary.core.updates.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.updates.service.PluginUpdates;

/** Small helpers of the update commands. */
public final class UpdateCatalogSupport {
    private UpdateCatalogSupport() {
    }

    /** The update actor that stands for this sender in the update history. */
    public static @NotNull UpdateActor actorOf(@NotNull CommandSender sender) {
        return new UpdateActor(AudienceExtensions.isConsole(sender) ? UpdateActor.Kind.CONSOLE : UpdateActor.Kind.PLAYER,
            AudienceExtensions.id(sender), AudienceExtensions.name(sender));
    }

    /** Runs the administrator operation exposed by the command and menu. */
    public static @NotNull CompletionStage<UpdatePlanSnapshot> forceLibrary(@NotNull PnLibrary library, @NotNull UpdateActor actor) {
        UpdateService updates = library.getUpdates();
        if (updates instanceof PluginUpdates) {
            return ((PluginUpdates) updates).forceLibrary(actor);
        }
        CompletableFuture<UpdatePlanSnapshot> result = new CompletableFuture<>();
        result.completeExceptionally(new IllegalStateException("Принудительное обновление pnLibrary недоступно."));
        return result;
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
