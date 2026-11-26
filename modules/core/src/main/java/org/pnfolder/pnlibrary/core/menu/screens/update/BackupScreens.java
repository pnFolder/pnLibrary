package org.pnfolder.pnlibrary.core.menu.screens.update;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuText;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.updates.history.BackupItemText;

/** Menu screens of the saved backups: the plugins that have copies, their copies by date and the rollback choices. */
public final class BackupScreens extends MenuScreens {
    private static final int DATABASE_BUTTONS = 3;
    private static final int DATABASE_FIRST = 15;
    private static final int FILES_BUTTON = 20;

    public BackupScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    /** First the plugins that have saved copies; a click lists that plugin's copies by date. */
    public void backups(MenuViewer player, int page) {
        data().backups().whenComplete((all, error) -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            List<Row> rows = new ArrayList<>();
            if (error == null) {
                for (Map.Entry<String, List<UpdateBackup>> entry : productsOf(all).entrySet()) {
                    rows.add(productRow(entry.getKey(), entry.getValue()));
                }
            }
            list(player, "Резервные копии", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> backups(who, to), routes::home,
                "Резервных копий нет");
        }));
    }

    private Row productRow(String product, List<UpdateBackup> sets) {
        Instant latest = null;
        for (UpdateBackup set : sets) {
            if (set.getCreatedAt() != null && (latest == null || set.getCreatedAt().isAfter(latest))) {
                latest = set.getCreatedAt();
            }
        }
        List<Fact> facts = Fact.list("Копий", String.valueOf(sets.size()));
        if (latest != null) {
            facts.add(new Fact("Последняя", MenuText.clockTime(latest)));
        }
        List<String> lore = Style.lore(Collections.emptyList(), facts, Style.left("выбрать дату"));
        return new Row(MenuItems.icon(Icons.PLUGIN, "PAPER", product, lore), go(who -> pluginBackups(who, product, 0)));
    }

    private static Map<String, List<UpdateBackup>> productsOf(List<UpdateBackup> all) {
        Map<String, List<UpdateBackup>> byProduct = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (UpdateBackup backup : all) {
            List<String> products = new ArrayList<>();
            for (UpdateBackupComponent component : backup.getComponents()) {
                addOnce(products, component.getProduct().getValue());
            }
            for (UpdateBackupItem item : backup.getItems()) {
                addOnce(products, item.getProduct().getValue());
            }
            for (String product : products) {
                List<UpdateBackup> sets = byProduct.get(product);
                if (sets == null) {
                    sets = new ArrayList<>();
                    byProduct.put(product, sets);
                }
                sets.add(backup);
            }
        }
        return byProduct;
    }

    private static void addOnce(List<String> products, String product) {
        if (!products.contains(product)) {
            products.add(product);
        }
    }

    private void pluginBackups(MenuViewer player, String product, int page) {
        data().backups().whenComplete((all, error) -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            List<UpdateBackup> own = new ArrayList<>();
            if (error == null) {
                for (UpdateBackup backup : all) {
                    if (productsOf(Collections.singletonList(backup)).containsKey(product)) {
                        own.add(backup);
                    }
                }
            }
            Collections.sort(own, byNewest());
            List<Row> rows = new ArrayList<>();
            for (UpdateBackup backup : own) {
                rows.add(new Row(backupItem(backup, Style.left("выбрать откат")), go(who -> backupChoice(who, backup, product))));
            }
            list(player, "Копии " + product, rows, page, MenuConstants.PAGE_SIZE, (who, to) -> pluginBackups(who, product, to),
                who -> backups(who, 0), "Копий нет");
        }));
    }

    private static Comparator<UpdateBackup> byNewest() {
        return (a, b) -> {
                if (a.getCreatedAt() == null || b.getCreatedAt() == null) {
                    return a.getCreatedAt() == null ? (b.getCreatedAt() == null ? 0 : 1) : -1;
                }
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            };
    }

    private void backupChoice(MenuViewer player, UpdateBackup backup, String product) {
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Откат")).rows(MenuConstants.SMALL_ROWS), MenuConstants.SMALL_ROWS);
        builder.slot(4, backupItem(backup));
        List<UpdateBackupItem> databases = databasesOf(backup, product);
        List<String> lines = new ArrayList<>(Arrays.asList("Вернутся сохранённые JAR.", "Конфигурации не изменятся.",
            "Применится после перезапуска."));
        if (!databases.isEmpty()) {
            lines.add("Базы данных не изменятся: их возвращают кнопками справа.");
        }
        List<String> plain = Style.lore(lines, Collections.emptyList(), Style.left("откатить"));
        builder.button(11, MenuItems.icon(Icons.ROLLBACK, "TNT", "Откатить плагины", plain, Tone.DANGER),
            go(who -> allowed(who, MenuConstants.ROLLBACK, () -> rollback(who, backup, false))));
        if (backup.getConfigurationFiles() > 0) {
            List<String> full = Style.lore(Arrays.asList(
                "Вернутся JAR и файлы настроек: " + backup.getConfigurationFiles() + ".", "Применится после перезапуска."),
                Collections.emptyList(), Style.left("откатить"));
            builder.button(13, MenuItems.icon(Icons.ROLLBACK, "TNT", "Откатить плагины и настройки", full, Tone.DANGER),
                go(who -> allowed(who, MenuConstants.ROLLBACK, () -> rollback(who, backup, true))));
        }
        databaseButtons(builder, backup, product, databases);
        if (hasFiles(backup, product)) {
            List<String> lore = Style.lore(Arrays.asList("Положит сохранённые базы и выгрузки в папку на сервере.",
                "Подойдёт, если базу нельзя вернуть кнопкой."), Collections.emptyList(), Style.left("выдать файлы"));
            builder.button(FILES_BUTTON, MenuItems.icon(Icons.BACKUPS, "CHEST", "Выдать файлы", lore),
                go(who -> allowed(who, MenuConstants.ROLLBACK, () -> extractFiles(who, backup, product))));
        }
        builder.button(MenuConstants.SMALL_BACK, MenuItems.backItem("Отмена"), go(who -> pluginBackups(who, product, 0)));
        show(player, builder.build());
    }

    private void databaseButtons(MenuBuilder builder, UpdateBackup backup, String product, List<UpdateBackupItem> databases) {
        for (int index = 0; index < Math.min(databases.size(), DATABASE_BUTTONS); index++) {
            UpdateBackupItem item = databases.get(index);
            List<String> lore = Style.lore(Arrays.asList("Заменит данные базы сохранёнными.", "Спросит подтверждение."),
                Fact.list("Размер копии", BackupItemText.size(item.getBytes())), Style.left("вернуть базу"));
            builder.button(DATABASE_FIRST + index,
                MenuItems.icon(Icons.BACKUPS, "CHEST", "Вернуть базу «" + item.getName() + "»", lore, Tone.DANGER),
                go(who -> allowed(who, MenuConstants.ROLLBACK, () -> databaseConfirm(who, backup, item, product))));
        }
    }

    private static boolean hasFiles(UpdateBackup backup, String product) {
        for (UpdateBackupItem item : backup.getItems()) {
            if (item.getKind() != BackupKind.CONFIGURATION && item.getProduct().getValue().equalsIgnoreCase(product)) {
                return true;
            }
        }
        return false;
    }

    private static List<UpdateBackupItem> databasesOf(UpdateBackup backup, String product) {
        List<UpdateBackupItem> databases = new ArrayList<>();
        for (UpdateBackupItem item : backup.getItems()) {
            if (item.getKind() == BackupKind.DATABASE && item.getProduct().getValue().equalsIgnoreCase(product)) {
                databases.add(item);
            }
        }
        return databases;
    }

    /** The last question before a database is replaced: what is lost, what is kept, and a way out. */
    private void databaseConfirm(MenuViewer player, UpdateBackup backup, UpdateBackupItem item, String product) {
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Вернуть базу")).rows(MenuConstants.SMALL_ROWS), MenuConstants.SMALL_ROWS);
        List<String> warning = Style.lore(Arrays.asList(
            "Таблицы базы «" + item.getName() + "» будут заменены теми, что были до обновления.",
            "Всё, что записано в базу с тех пор, пропадёт: игроки, балансы, прогресс.",
            "Текущее состояние сохранится отдельной копией на сервере, вернуть его можно командой."),
            Fact.list("Плагин", item.getProduct().getValue(), "Копия", BackupItemText.size(item.getBytes())), Style.left("подтвердить"));
        builder.slot(4, MenuItems.icon(Icons.BACKUPS, "CHEST", "Вы можете потерять данные", warning, Tone.DANGER));
        builder.button(11, MenuItems.icon(Icons.ROLLBACK, "TNT", "Да, заменить базу", warning, Tone.DANGER),
            go(who -> allowed(who, MenuConstants.ROLLBACK, () -> restoreDatabase(who, backup, item))));
        builder.button(MenuConstants.SMALL_BACK, MenuItems.backItem("Отмена"), go(who -> backupChoice(who, backup, product)));
        show(player, builder.build());
    }

    private void extractFiles(MenuViewer player, UpdateBackup backup, String product) {
        player.close();
        data().extractBackup(backup.getId(), product, player.getActor()).whenComplete((folder, error) -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                tell(player, ChatTone.FAILURE, "Не удалось выдать файлы.", Throwables.rootMessage(error));
            } else {
                tell(player, ChatTone.SUCCESS, "Файлы плагина " + product + " выданы.", "Папка на сервере: " + folder);
            }
        }));
    }

    private void restoreDatabase(MenuViewer player, UpdateBackup backup, UpdateBackupItem item) {
        player.close();
        data().restoreDatabase(backup.getId(), item.getProduct().getValue(), item.getName(), player.getActor())
            .whenComplete((done, error) -> sync(() -> {
                if (!player.isOnline()) {
                    return;
                }
                if (error != null) {
                    tell(player, ChatTone.FAILURE, "Не удалось вернуть базу.", Throwables.rootMessage(error));
                } else {
                    tell(player, ChatTone.SUCCESS, "База «" + item.getName() + "» возвращена.",
                        "Перезапустите плагин " + item.getProduct().getValue() + ", чтобы он перечитал данные.");
                }
            }));
    }

    private void rollback(MenuViewer player, UpdateBackup backup, boolean restoreConfigurations) {
        player.close();
        data().rollback(backup.getId(), restoreConfigurations, player.getActor()).whenComplete((done, error) -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                tell(player, ChatTone.FAILURE, "Не удалось подготовить откат.", Throwables.rootMessage(error));
            } else {
                tell(player, ChatTone.SUCCESS, "Резервный набор подготовлен.", "Полностью перезапустите сервер, чтобы применить его.");
            }
        }));
    }

    private static ItemSpec backupItem(UpdateBackup backup, String... clicks) {
        List<Fact> parameters = new ArrayList<>();
        if (backup.getCreatedAt() != null) {
            parameters.add(new Fact("Создана", MenuText.clockTime(backup.getCreatedAt())));
        }
        for (UpdateBackupComponent component : backup.getComponents()) {
            parameters.add(new Fact(component.getProduct().getValue(), (component.getBackupVersion() == null ? "?" : component.getBackupVersion())
                + " (было " + (component.getUpdatedVersion() == null ? "?" : component.getUpdatedVersion()) + ")"));
        }
        for (UpdateBackupItem item : backup.getItems()) {
            parameters.add(new Fact("Сохранено", BackupItemText.describe(item)));
        }
        return MenuItems.icon(Icons.BACKUPS, "CHEST", "Резервный набор",
            Style.lore(Collections.emptyList(), parameters, clicks));
    }
}
