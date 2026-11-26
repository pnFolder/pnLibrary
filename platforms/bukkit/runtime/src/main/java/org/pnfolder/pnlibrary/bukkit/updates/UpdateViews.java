package org.pnfolder.pnlibrary.bukkit.updates;

import java.time.Instant;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogRoutes;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;

/** The catalog renderer of core with the time and zone that every Bukkit caller wants filled in. */
public final class UpdateViews {
    private UpdateViews() {
    }

    /** The chat announcement of pending updates; empty when there is nothing to tell. */
    public static List<Component> announcement(List<UpdateSnapshot> snapshots, UpdateCatalogRoutes routes, String support) {
        return UpdateCatalogView.announcement(snapshots, routes, support, Instant.now(), DisplayZone.getZone());
    }

    /** The status card of the server and its components. */
    public static List<Component> status(List<org.pnfolder.pnlibrary.common.lang.Pair<String, String>> facts, List<UpdateSnapshot> snapshots,
                                         UpdateCatalogRoutes routes, String support) {
        return UpdateCatalogView.status(facts, snapshots, routes, support, Instant.now(), DisplayZone.getZone());
    }

    /** One or several short notices rendered the way the catalog renders them. */
    public static List<Component> notices(List<ChatNotice> notices, UpdateCatalogRoutes routes) {
        return UpdateCatalogView.notices(notices, routes);
    }

    /** The same routes for a reader that may or may not click and may need ASCII. */
    public static UpdateCatalogRoutes forReader(UpdateCatalogRoutes routes, boolean interactive, boolean ascii) {
        return new UpdateCatalogRoutes(routes.getRoot(), routes.getUpdate(), routes.getCheck(), interactive, ascii,
            routes.getEditable());
    }
}
