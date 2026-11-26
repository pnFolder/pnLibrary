package org.pnfolder.pnlibrary.core.menu;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;
import org.pnfolder.pnlibrary.core.menu.data.MenuData;
import org.pnfolder.pnlibrary.core.menu.data.PluginRow;
import org.pnfolder.pnlibrary.core.menu.data.ViewerInfo;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuPermissions;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;

/** State of one open menu: the data source and the server whose menu it is. */
public final class MenuSession {
    private final MenuData data;
    private final MenuHost host;
    private final String remoteName;
    private final MenuList lists = new MenuList(this);

    public MenuSession(@NotNull MenuData data, @NotNull MenuHost host) {
        this(data, host, null);
    }

    public MenuSession(@NotNull MenuData data, @NotNull MenuHost host, @Nullable String remoteName) {
        this.data = data;
        this.host = host;
        this.remoteName = remoteName;
    }

    public @NotNull MenuData getData() {
        return data;
    }

    public @NotNull MenuHost getHost() {
        return host;
    }

    public boolean isRemote() {
        return remoteName != null;
    }

    public void allowed(@NotNull MenuViewer player, @NotNull String permission, @NotNull Runnable action) {
        if (player.hasPermission(permission) || player.hasPermission(MenuPermissions.ADMIN)) {
            action.run();
        } else {
            tell(player, ChatTone.FAILURE, "Не хватает права.", permission);
        }
    }

    public void deliver(@NotNull MenuViewer player, @NotNull List<Component> lines) {
        player.send(lines);
    }

    public void tell(@NotNull MenuViewer player, @NotNull ChatTone tone, @NotNull String text) {
        tell(player, tone, text, null);
    }

    public void tell(@NotNull MenuViewer player, @NotNull ChatTone tone, @NotNull String text, @Nullable String detail) {
        deliver(player, UpdateCatalogView.notice(new ChatNotice(tone, text, detail), MenuConstants.ROUTES));
    }

    /** A page of a list whose rows are the whole list, cut into pages here. */
    public void list(@NotNull MenuViewer player, @NotNull String title, @NotNull List<Row> rows, int page, int perPage,
                     @NotNull BiConsumer<MenuViewer, Integer> reopen, @NotNull Consumer<MenuViewer> back,
                     @NotNull String emptyText) {
        list(player, title, rows, page, perPage, reopen, back, emptyText, rows.size(), builder -> { });
    }

    /** Same as the plain list, with {@code extra} adding buttons to the footer. */
    public void list(@NotNull MenuViewer player, @NotNull String title, @NotNull List<Row> rows, int page, int perPage,
                     @NotNull BiConsumer<MenuViewer, Integer> reopen, @NotNull Consumer<MenuViewer> back,
                     @NotNull String emptyText, @NotNull Consumer<MenuBuilder> extra) {
        list(player, title, rows, page, perPage, reopen, back, emptyText, rows.size(), extra);
    }

    /**
     * A page of a list. The rows are the rows of {@code page} when {@code total} says the list is larger than the
     * rows, otherwise the whole list that is cut into pages here. {@code reopen} receives the player and the page to open.
     */
    public void list(@NotNull MenuViewer player, @NotNull String title, @NotNull List<Row> rows, int page, int perPage,
                     @NotNull BiConsumer<MenuViewer, Integer> reopen, @NotNull Consumer<MenuViewer> back,
                     @NotNull String emptyText, int total, @NotNull Consumer<MenuBuilder> extra) {
        lists.show(player, title, rows, page, perPage, reopen, back, emptyText, total, extra);
    }

    public @NotNull MenuClickHandler go(@NotNull Consumer<MenuViewer> action) {
        return click -> away(click.getPlayer(), () -> action.accept(click.getPlayer()));
    }

    public @NotNull MenuClickHandler goClick(@NotNull BiConsumer<MenuViewer, ClickType> action) {
        return click -> away(click.getPlayer(), () -> action.accept(click.getPlayer(), click.getClick()));
    }

    /** Runs {@code action} where it may wait for the data: here at once, for another server away from the server thread. */
    public void away(@NotNull Runnable action) {
        away(null, action);
    }

    /** Same as {@link #away(Runnable)}; a failure on the other server is told to {@code player}. */
    public void away(@Nullable MenuViewer player, @NotNull Runnable action) {
        if (!isRemote()) {
            action.run();
            return;
        }
        host.async(() -> {
            try {
                action.run();
            } catch (Exception error) {
                if (player != null) {
                    tell(player, ChatTone.FAILURE, "Сервер " + remoteName + " не ответил.", Throwables.rootMessage(error));
                }
            }
        });
    }

    public void sync(@NotNull Runnable action) {
        if (isRemote()) {
            away(null, action);
        } else {
            host.sync(action);
        }
    }

    public void later(long delayMillis, @NotNull Runnable action) {
        host.later(delayMillis, () -> away(null, action));
    }

    public void runCommand(@NotNull MenuViewer player, @NotNull String line) {
        if (!isRemote()) {
            player.runCommand(line);
            return;
        }
        away(player, () -> {
            Set<String> held = new HashSet<>();
            for (String permission : MenuPermissions.ALL) {
                if (player.hasPermission(permission)) {
                    held.add(permission);
                }
            }
            List<Component> lines;
            try {
                lines = data.command(new ViewerInfo(player.getName(), held), line);
            } catch (Exception error) {
                String reason = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
                lines = new ChatKit(ChatReader.PLAYER).notice(
                    new ChatNotice(ChatTone.FAILURE, "Команда не выполнилась: /" + line, reason));
            }
            player.send(lines);
        });
    }

    public void show(@NotNull MenuViewer player, @NotNull Menu menu) {
        player.open(menu);
    }

    public @NotNull List<UpdateSnapshot> snapshots() {
        return data.snapshots();
    }

    public int connectedCount() {
        Set<String> names = new HashSet<>();
        for (UpdateSnapshot snapshot : snapshots()) {
            names.add(snapshot.getProduct().toLowerCase(Locale.ROOT));
        }
        int count = names.size();
        for (PluginRow plugin : data.plugins()) {
            if (!names.contains(plugin.getName().toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }
}
