package org.pnfolder.pnlibrary.core.menu;

import org.pnfolder.pnlibrary.core.menu.session.ClickType;
import org.pnfolder.pnlibrary.core.menu.session.MenuClickHandler;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import java.util.Objects;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.core.menu.data.MenuData;
import org.pnfolder.pnlibrary.core.menu.style.Row;

/** The helpers every screen group uses; they live in the {@link MenuSession} and are only forwarded here. */
public abstract class MenuScreens {
    private final MenuSession session;
    protected final MenuRoutes routes;

    protected MenuScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        this.session = Objects.requireNonNull(session, "session");
        this.routes = Objects.requireNonNull(routes, "routes");
    }

    protected MenuData data() {
        return session.getData();
    }

    protected MenuHost host() {
        return session.getHost();
    }

    protected boolean remote() {
        return session.isRemote();
    }

    protected MenuClickHandler go(Consumer<MenuViewer> action) {
        return session.go(action);
    }

    protected MenuClickHandler goClick(BiConsumer<MenuViewer, ClickType> action) {
        return session.goClick(action);
    }

    protected void away(Runnable action) {
        session.away(action);
    }

    protected void away(@Nullable MenuViewer player, Runnable action) {
        session.away(player, action);
    }

    protected void sync(Runnable action) {
        session.sync(action);
    }

    protected void later(long delayMillis, Runnable action) {
        session.later(delayMillis, action);
    }

    protected void runCommand(MenuViewer player, String line) {
        session.runCommand(player, line);
    }

    protected void show(MenuViewer player, Menu menu) {
        session.show(player, menu);
    }

    protected void allowed(MenuViewer player, String permission, Runnable action) {
        session.allowed(player, permission, action);
    }

    protected void deliver(MenuViewer player, List<Component> lines) {
        session.deliver(player, lines);
    }

    protected void tell(MenuViewer player, ChatTone tone, String text) {
        session.tell(player, tone, text);
    }

    protected void tell(MenuViewer player, ChatTone tone, String text, @Nullable String detail) {
        session.tell(player, tone, text, detail);
    }

    /** Closes the menu and offers {@code command} to the viewer to put into the chat line; {@code text} tells what to type. */
    protected void suggestCommand(MenuViewer player, String text, String command) {
        player.close();
        ChatKit kit = new ChatKit(ChatReader.PLAYER);
        Component line = kit.mark(ChatTone.INFO).append(kit.text(text, ChatPalette.TEXT)).append(kit.text(" "))
            .append(kit.bracketed("Ввести команду", ChatPalette.ACCENT))
            .clickEvent(ClickEvent.suggestCommand(command))
            .hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы вставить в чат", ChatPalette.TEXT),
                kit.text(command.trim(), ChatPalette.LABEL)));
        player.send(Collections.singletonList(line));
    }

    protected void list(MenuViewer player, String title, List<Row> rows, int page, int perPage,
                        BiConsumer<MenuViewer, Integer> reopen, Consumer<MenuViewer> back, String emptyText) {
        session.list(player, title, rows, page, perPage, reopen, back, emptyText);
    }

    protected void list(MenuViewer player, String title, List<Row> rows, int page, int perPage,
                        BiConsumer<MenuViewer, Integer> reopen, Consumer<MenuViewer> back, String emptyText,
                        Consumer<MenuBuilder> extra) {
        session.list(player, title, rows, page, perPage, reopen, back, emptyText, extra);
    }

    protected void list(MenuViewer player, String title, List<Row> rows, int page, int perPage,
                        BiConsumer<MenuViewer, Integer> reopen, Consumer<MenuViewer> back, String emptyText,
                        int total, Consumer<MenuBuilder> extra) {
        session.list(player, title, rows, page, perPage, reopen, back, emptyText, total, extra);
    }

    protected List<UpdateSnapshot> snapshots() {
        return session.snapshots();
    }

    protected int connectedCount() {
        return session.connectedCount();
    }
}
