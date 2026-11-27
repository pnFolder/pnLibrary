package org.pnfolder.pnlibrary.core.menu.style;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogRoutes;

/** Slots, sizes and permissions the menu screens share. */
public final class MenuConstants {
    public static final UpdateCatalogRoutes ROUTES =
        new UpdateCatalogRoutes("/pn updates", product -> "/pn update " + product, "/pn check");
    public static final String SETTINGS = MenuPermissions.UPDATES_SETTINGS;
    public static final String ROLLBACK = MenuPermissions.UPDATES_ROLLBACK;
    public static final int HOME_ROWS = 5;
    public static final int NETWORK_CHECK = 49;
    public static final String GROUPS_PERMISSION = "pnlibrary.groups";
    public static final int HOME_CLOSE = 40;
    public static final int SMALL_ROWS = 3;
    public static final int SMALL_BACK = 22;
    public static final int PAGE_SIZE = 45;
    public static final int HISTORY_PAGE = 10;
    public static final int FOOTER_END = 54;
    public static final int BACK = 45;
    public static final int PREVIOUS = 48;
    public static final int NEXT = 50;
    public static final int FILTER = 47;
    public static final int DATE_FILTER = 46;
    public static final int JOURNAL_HOURS = 24;
    public static final int JOURNAL_LIMIT = 200;
    public static final int MESSAGE_PREVIEW = 70;
    public static final String BUS = MenuPermissions.EVENTS_BUS;
    public static final String TASKS = MenuPermissions.TASKS;
    public static final String STATS = MenuPermissions.STATS;
    public static final String PLACEHOLDERS = MenuPermissions.PLACEHOLDERS;
    public static final String CHANNELS = MenuPermissions.CHANNELS;
    public static final String ACTIONS = MenuPermissions.ACTIONS;
    public static final String CONFIGS = "pnlibrary.configs";
    public static final int DETAIL_ROWS = 5;
    public static final int DETAIL_BACK = 40;
    public static final int TITLE_LIMIT = 30;

    /** The lengths of pause the menu offers. */
    public static final List<HoldOption> HOLDS = Collections.unmodifiableList(Arrays.asList(
        new HoldOption("1 час", Duration.ofHours(1)),
        new HoldOption("6 часов", Duration.ofHours(6)),
        new HoldOption("1 день", Duration.ofDays(1)),
        new HoldOption("3 дня", Duration.ofDays(3)),
        new HoldOption("5 дней", Duration.ofDays(5))));

    private MenuConstants() {
    }
}
