package org.pnfolder.pnlibrary.core.menu.screens;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.commands.modules.DiskUsage;
import org.pnfolder.pnlibrary.core.commands.modules.LibraryRow;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** The libraries plugins and modules registered, by plugin or all in one list. */
public final class LibraryScreens extends MenuScreens {
    private static final int SHOWN = 40;

    public LibraryScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    /**
     * Without {@code owner} a list of plugins to choose from, each with its libraries counted; with a plugin its
     * libraries, with {@code plugin:module} the libraries of that module.
     */
    public void libraries(MenuViewer player, @Nullable String owner, int page, Consumer<MenuViewer> back) {
        if (owner == null) {
            pluginsWithLibraries(player, page, back);
            return;
        }
        List<Row> rows = new ArrayList<>();
        for (LibraryRow row : data().libraries()) {
            if (row.belongsTo(owner)) {
                rows.add(new Row(libraryItem(row, owner.contains(":") ? row.getTitle() : row.getTitle() + " · " + row.getPlugin()),
                    go(who -> { })));
            }
        }
        list(player, "Библиотеки: " + owner, rows, page, MenuConstants.PAGE_SIZE,
            (who, to) -> libraries(who, owner, to, back), back, "Библиотек нет");
    }

    /** Every library of every plugin in one list, for when the plugin does not matter. */
    private void allLibraries(MenuViewer player, int page, Consumer<MenuViewer> back) {
        List<Row> rows = new ArrayList<>();
        for (LibraryRow row : data().libraries()) {
            rows.add(new Row(libraryItem(row, row.getTitle() + " · " + row.getPlugin()), go(who -> { })));
        }
        list(player, "Все библиотеки", rows, page, MenuConstants.PAGE_SIZE,
            (who, to) -> allLibraries(who, to, back), back, "Библиотек нет");
    }

    private void pluginsWithLibraries(MenuViewer player, int page, Consumer<MenuViewer> back) {
        List<LibraryRow> all = data().libraries();
        Consumer<MenuViewer> reopen = who -> libraries(who, null, page, back);
        Set<String> names = new LinkedHashSet<>();
        Map<String, List<LibraryRow>> byPlugin = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (LibraryRow row : all) {
            names.add(row.getPlugin());
            List<LibraryRow> own = byPlugin.get(row.getPlugin());
            if (own == null) {
                own = new ArrayList<>();
                byPlugin.put(row.getPlugin(), own);
            }
            own.add(row);
        }
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(MenuItems.icon(null, "BOOKSHELF", "Все библиотеки", Style.lore(
            Collections.singletonList("Одним списком, у каждой написано, чья она."),
            Fact.list("Библиотек", String.valueOf(all.size()), "Плагинов", String.valueOf(names.size())),
            Style.left("открыть"))), go(who -> allLibraries(who, 0, reopen))));
        for (Map.Entry<String, List<LibraryRow>> entry : byPlugin.entrySet()) {
            rows.add(pluginRow(entry.getKey(), entry.getValue(), reopen));
        }
        list(player, "Библиотеки", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> libraries(who, null, to, back), back, "Библиотек нет");
    }

    private Row pluginRow(String plugin, List<LibraryRow> own, Consumer<MenuViewer> reopen) {
        Set<String> modules = new LinkedHashSet<>();
        Set<String> files = new LinkedHashSet<>();
        boolean conflict = false;
        for (LibraryRow row : own) {
            if (row.getModule() != null) {
                modules.add(row.getModule());
            }
            if (row.getFile() != null) {
                files.add(row.getFile());
            }
            conflict |= row.getConflict();
        }
        long size = 0;
        for (String file : files) {
            size += new File(file).length();
        }
        List<Fact> parameters = new ArrayList<>();
        parameters.add(new Fact("Библиотек", String.valueOf(own.size())));
        if (!modules.isEmpty()) {
            String joined = String.join(", ", modules);
            parameters.add(new Fact("Модулей", modules.size() + ": " + (joined.length() > SHOWN ? joined.substring(0, SHOWN) : joined)));
        }
        if (size > 0) {
            parameters.add(new Fact("Размер", DiskUsage.format(size)));
        }
        if (conflict) {
            parameters.add(new Fact("Внимание", "другая версия у другого плагина"));
        }
        return new Row(MenuItems.icon(null, conflict ? "BARRIER" : "CHEST", plugin,
            Style.lore(Collections.singletonList("Что подключили сам плагин и его модули."), parameters, Style.left("открыть")),
            conflict ? Tone.DANGER : Tone.NORMAL), go(who -> libraries(who, plugin, 0, reopen)));
    }

    private static ItemSpec libraryItem(LibraryRow row, String title) {
        List<Fact> parameters = new ArrayList<>();
        parameters.add(new Fact("Версия", row.getVersion()));
        parameters.add(new Fact("Принадлежит", row.getOwnerText()));
        parameters.add(new Fact("Загрузка", scope(row.getScope())));
        if (row.getSize() >= 0) {
            parameters.add(new Fact("Размер", DiskUsage.format(row.getSize())));
        }
        if (row.getFileName() != null) {
            parameters.add(new Fact("Файл", lastText(row.getFileName(), SHOWN, false)));
        }
        if (row.getFolder() != null) {
            parameters.add(new Fact("Папка", lastText(row.getFolder(), SHOWN, false)));
        }
        parameters.add(new Fact("Сумма", LibraryRow.GIVEN.equals(row.getTrust()) ? "задана автором" : "jar не проверяется"));
        if (!row.getRequired()) {
            parameters.add(new Fact("Обязательная", "нет"));
        }
        if (row.getConflict()) {
            parameters.add(new Fact("Внимание", "другая версия у другого плагина"));
        }
        if (!row.getRepositories().isEmpty()) {
            String source = row.getRepositories().get(0);
            parameters.add(new Fact("Откуда", lastText(source.startsWith("https://") ? source.substring(8) : source, SHOWN, true)));
        }
        List<String> description = row.getDescription() == null
            ? Collections.singletonList("Автор не описал эту библиотеку.")
            : CoreFormatting.wrapText(row.getDescription(), CoreFormatting.WRAP_WIDTH, 3);
        return MenuItems.icon(null, row.getConflict() ? "BARRIER" : "BOOK", title, Style.lore(description, parameters),
            Tone.of(row.getConflict(), false, !row.getConflict() && !LibraryRow.NONE.equals(row.getTrust()), false));
    }

    private static String scope(String scope) {
        if ("ISOLATED".equals(scope)) {
            return "отдельно";
        }
        return "LIBRARY".equals(scope) ? "в pnLibrary" : "в плагине";
    }

    /** Cuts a long text to {@code limit} characters with an ellipsis: the end is kept (the folder), or the start when {@code fromStart}. */
    private static String lastText(String text, int limit, boolean fromStart) {
        if (text.length() <= limit) {
            return text;
        }
        return fromStart ? text.substring(0, limit - 1) + "…" : "…" + text.substring(text.length() - (limit - 1));
    }
}
