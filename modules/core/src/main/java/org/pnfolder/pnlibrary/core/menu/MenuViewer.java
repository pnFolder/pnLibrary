package org.pnfolder.pnlibrary.core.menu;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;

/** Whoever looks at the menu: a player on this server, or a player of another server whose pages are sent over the link. */
public interface MenuViewer {
    @NotNull UUID getId();

    @NotNull String getName();

    boolean isOnline();

    boolean isRemote();

    @NotNull UpdateActor getActor();

    boolean hasPermission(@NotNull String permission);

    void open(@NotNull Menu menu);

    void close();

    void send(@NotNull List<? extends Component> lines);

    /** Runs {@code command}, without the slash, with the viewer's permissions; what it prints goes to the viewer. */
    void runCommand(@NotNull String command);
}
