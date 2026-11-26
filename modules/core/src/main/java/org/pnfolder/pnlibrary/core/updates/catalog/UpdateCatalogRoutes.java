package org.pnfolder.pnlibrary.core.updates.catalog;

import java.util.Objects;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Commands a catalog page links to, so one view serves both the Bukkit {@code /pn updates} tree and the
 * console-only {@code /pn update} command of the proxies.
 */
public final class UpdateCatalogRoutes {
    private final String root;
    private final Function<String, String> update;
    private final String check;
    private final boolean interactive;
    private final boolean ascii;
    private final boolean editable;

    /**
     * @param root base of the catalog commands, for example {@code /pn updates}
     * @param update builds the command that prepares an update for one product; null hides update buttons
     * @param check command that refreshes the release catalogs
     * @param interactive whether the reader can click; without it buttons are replaced by a list of commands
     * @param ascii whether to avoid characters outside ASCII, for consoles with a legacy code page
     * @param editable whether the reader may change settings; otherwise the settings card is read-only
     */
    public UpdateCatalogRoutes(@NotNull String root, @Nullable Function<String, String> update, @NotNull String check,
                               boolean interactive, boolean ascii, boolean editable) {
        this.root = root;
        this.update = update;
        this.check = check;
        this.interactive = interactive;
        this.ascii = ascii;
        this.editable = editable;
    }

    public UpdateCatalogRoutes(@NotNull String root, @Nullable Function<String, String> update, @NotNull String check) {
        this(root, update, check, true, false, true);
    }

    public UpdateCatalogRoutes(@NotNull String root, @Nullable Function<String, String> update) {
        this(root, update, root + " check", true, false, true);
    }

    public UpdateCatalogRoutes(@NotNull String root) {
        this(root, null);
    }

    public @NotNull String getRoot() {
        return root;
    }

    public @Nullable Function<String, String> getUpdate() {
        return update;
    }

    public @NotNull String getCheck() {
        return check;
    }

    public boolean getInteractive() {
        return interactive;
    }

    public boolean getAscii() {
        return ascii;
    }

    public boolean getEditable() {
        return editable;
    }

    /** The same routes for a reader that can or cannot click and may or may not edit. */
    public @NotNull UpdateCatalogRoutes forReader(boolean interactive, boolean ascii, boolean editable) {
        return new UpdateCatalogRoutes(root, update, check, interactive, ascii, editable);
    }

    /** Command that lists every installed product. */
    public @NotNull String getList() {
        return root + " list";
    }

    /** Command that lists products with a newer release available. */
    public @NotNull String getAvailable() {
        return root + " available";
    }

    /** Command that shows the update history. */
    public @NotNull String getHistory() {
        return root + " history";
    }

    /** Command that lists the available rollback backups. */
    public @NotNull String getBackups() {
        return root + " backups";
    }

    /** Command that shows the details of one product. */
    public @NotNull String info(@NotNull String product) {
        return root + " info " + product;
    }

    /** Command that opens the settings of one product. */
    public @NotNull String settings(@NotNull String product) {
        return root + " settings " + product;
    }

    /** Command that shows the update history of one product. */
    public @NotNull String history(@NotNull String product) {
        return root + " history plugin " + product;
    }

    /** Command that changes one setting of one product to {@code value}. */
    public @NotNull String set(@NotNull String product, @NotNull String setting, @NotNull String value) {
        return root + " set " + product + " " + setting + " " + value;
    }

    /** Command that restores the backup {@code id}. */
    public @NotNull String rollback(@NotNull Object id) {
        return root + " rollback " + id;
    }

    /** Command that restores the backup {@code id}, with its configuration files when {@code configurations}. */
    public @NotNull String rollback(@NotNull Object id, boolean configurations) {
        return rollback(id) + (configurations ? " configs" : "");
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UpdateCatalogRoutes)) {
            return false;
        }
        UpdateCatalogRoutes that = (UpdateCatalogRoutes) other;
        return root.equals(that.root) && Objects.equals(update, that.update) && check.equals(that.check)
            && interactive == that.interactive && ascii == that.ascii && editable == that.editable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, update, check, interactive, ascii, editable);
    }

    @Override
    public String toString() {
        return "UpdateCatalogRoutes(root=" + root + ", interactive=" + interactive + ", ascii=" + ascii + ", editable=" + editable + ")";
    }
}
