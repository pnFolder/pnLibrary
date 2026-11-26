package org.pnfolder.pnlibrary.core.updates.catalog;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.pnfolder.pnlibrary.common.lang.Pair;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ConfirmChoice;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.common.time.DisplayZone;

/**
 * Chat and console pages of the managed update catalog, in the shared {@link ChatKit} style.
 *
 * <p>Each page is rendered by its own screen; this class is the single entry point for commands. Nothing here calls
 * the server or changes updates. Every page has a short form that reads the clock and the display zone itself.
 */
public final class UpdateCatalogView {
    /** Entries per page, shared with the history reader. */
    public static final int PAGE_SIZE = 10;

    /** Channels a component can be switched between, in order of increasing risk. */
    public static final List<UpdateChannel> CHANNELS = Collections.unmodifiableList(Arrays.asList(UpdateChannel.values()));

    /** Pause periods offered on the settings card, as accepted by {@code set <plugin> hold}; a pause lasts at most a week. */
    public static final List<String> HOLD_PERIODS = Collections.unmodifiableList(Arrays.asList("1h", "6h", "1d", "3d", "7d"));

    private UpdateCatalogView() {
    }

    private static Instant now() {
        return Instant.now();
    }

    private static ZoneId zone() {
        return DisplayZone.getZone();
    }

    /** One page of registered components, never the native plugin inventory. */
    public static @NotNull List<Component> catalog(@NotNull List<UpdateSnapshot> snapshots, int page, boolean updatesOnly,
                                                   @NotNull UpdateCatalogRoutes routes) {
        return catalog(snapshots, page, updatesOnly, routes, now(), zone());
    }

    /** Same as the short form; {@code now} is the reference for release ages in the hover cards. */
    public static @NotNull List<Component> catalog(@NotNull List<UpdateSnapshot> snapshots, int page, boolean updatesOnly,
                                                   @NotNull UpdateCatalogRoutes routes, @NotNull Instant now, @NotNull ZoneId zone) {
        return new CatalogScreen(routes, now, zone).catalog(snapshots, page, updatesOnly);
    }

    /** Durable events newest first, grouped by day. */
    public static @NotNull List<Component> history(@NotNull List<UpdateHistoryEntry> entries, int page, @NotNull UpdateCatalogRoutes routes) {
        return history(entries, page, routes, null, null, now(), zone());
    }

    /**
     * Same as the short form.
     *
     * @param totalPages number of pages when known; otherwise only a full page of entries implies another
     * @param product when set, the title shows the filter and the lines do not offer to filter again
     */
    public static @NotNull List<Component> history(@NotNull List<UpdateHistoryEntry> entries, int page, @NotNull UpdateCatalogRoutes routes,
                                                   @Nullable Integer totalPages, @Nullable String product, @NotNull Instant now,
                                                   @NotNull ZoneId zone) {
        return new HistoryScreen(routes, now, zone).history(entries, page, totalPages, product);
    }

    /** Retained rollback sets with their age; paths and file contents are never shown. */
    public static @NotNull List<Component> backups(@NotNull List<UpdateBackup> backups, int page, @NotNull UpdateCatalogRoutes routes) {
        return backups(backups, page, routes, now(), zone(), new BackupQuery());
    }

    public static @NotNull List<Component> backups(@NotNull List<UpdateBackup> backups, int page, @NotNull UpdateCatalogRoutes routes,
                                                   @NotNull BackupQuery query) {
        return backups(backups, page, routes, now(), zone(), query);
    }

    public static @NotNull List<Component> backups(@NotNull List<UpdateBackup> backups, int page, @NotNull UpdateCatalogRoutes routes,
                                                   @NotNull Instant now, @NotNull ZoneId zone, @NotNull BackupQuery query) {
        return new BackupsScreen(routes, now, zone).backups(backups, page, query);
    }

    /** The details card of one component: versions, release date, other channels and its pause. */
    public static @NotNull List<Component> info(@NotNull UpdateSnapshot snapshot, @NotNull UpdateCatalogRoutes routes,
                                                @Nullable Instant heldUntil) {
        return info(snapshot, routes, heldUntil, now(), zone());
    }

    public static @NotNull List<Component> info(@NotNull UpdateSnapshot snapshot, @NotNull UpdateCatalogRoutes routes,
                                                @Nullable Instant heldUntil, @NotNull Instant now, @NotNull ZoneId zone) {
        return new ProductScreens(routes, now, zone).info(snapshot, heldUntil);
    }

    /** The settings card of one component: channel, automatic download and a pause for a chosen period. */
    public static @NotNull List<Component> settings(@NotNull UpdateSnapshot snapshot, @NotNull UpdateCatalogRoutes routes,
                                                    @Nullable Instant heldUntil) {
        return settings(snapshot, routes, heldUntil, now(), zone());
    }

    public static @NotNull List<Component> settings(@NotNull UpdateSnapshot snapshot, @NotNull UpdateCatalogRoutes routes,
                                                    @Nullable Instant heldUntil, @NotNull Instant now, @NotNull ZoneId zone) {
        return new ProductScreens(routes, now, zone).settings(snapshot, heldUntil);
    }

    /**
     * The state of the server and every registered component, for {@code /pn status}.
     *
     * @param facts labelled values shown before the components, such as the server core and Java
     * @param support link opened by the support button, or null to hide it
     */
    public static @NotNull List<Component> status(@NotNull List<Pair<String, String>> facts, @NotNull List<UpdateSnapshot> snapshots,
                                                  @NotNull UpdateCatalogRoutes routes, @Nullable String support) {
        return status(facts, snapshots, routes, support, now(), zone());
    }

    public static @NotNull List<Component> status(@NotNull List<Pair<String, String>> facts, @NotNull List<UpdateSnapshot> snapshots,
                                                  @NotNull UpdateCatalogRoutes routes, @Nullable String support, @NotNull Instant now,
                                                  @NotNull ZoneId zone) {
        return new CatalogScreen(routes, now, zone).status(facts, snapshots, support);
    }

    /** The notice an administrator gets on joining: only the components with an available or prepared update, or nothing. */
    public static @NotNull List<Component> announcement(@NotNull List<UpdateSnapshot> snapshots, @NotNull UpdateCatalogRoutes routes,
                                                        @Nullable String support) {
        return announcement(snapshots, routes, support, now(), zone());
    }

    public static @NotNull List<Component> announcement(@NotNull List<UpdateSnapshot> snapshots, @NotNull UpdateCatalogRoutes routes,
                                                        @Nullable String support, @NotNull Instant now, @NotNull ZoneId zone) {
        return new CatalogScreen(routes, now, zone).announcement(snapshots, support);
    }

    /** The support card with a button that opens {@code link}. */
    public static @NotNull List<Component> support(@NotNull String link, @NotNull UpdateCatalogRoutes routes) {
        return new CatalogScreen(routes, now(), zone()).support(link);
    }

    /** One short message of an update command: a mark in the tone's color, the text and its detail. */
    public static @NotNull List<Component> notice(@NotNull ChatNotice notice, @NotNull UpdateCatalogRoutes routes) {
        return kit(routes).notice(notice);
    }

    /** The notices one after another, as a single reply. */
    public static @NotNull List<Component> notices(@NotNull List<ChatNotice> notices, @NotNull UpdateCatalogRoutes routes) {
        List<Component> lines = new ArrayList<>();
        for (ChatNotice notice : notices) {
            lines.addAll(notice(notice, routes));
        }
        return lines;
    }

    /**
     * A card that asks to confirm a risky action, such as a rollback or a restart.
     *
     * @param lines what will happen, one sentence per line
     * @param command the command that confirms; a reader that cannot click is told to type it
     */
    public static @NotNull List<Component> confirmation(@NotNull String title, @NotNull List<String> lines, @NotNull String label,
                                                        @NotNull String command, @NotNull String hint, @NotNull UpdateCatalogRoutes routes) {
        return kit(routes).confirmation(title, lines, label, command, hint);
    }

    /** A confirmation card with several answers on one line, such as restarting now or later. */
    public static @NotNull List<Component> confirmationChoices(@NotNull String title, @NotNull List<String> lines,
                                                               @NotNull List<ConfirmChoice> choices, @NotNull UpdateCatalogRoutes routes) {
        return kit(routes).confirmationChoices(title, lines, choices);
    }

    private static ChatKit kit(UpdateCatalogRoutes routes) {
        return new ChatKit(new ChatReader(routes.getInteractive(), routes.getAscii()));
    }
}
