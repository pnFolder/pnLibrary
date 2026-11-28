package org.pnfolder.pnlibrary.core.menu.screens;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskPeriod;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskState;
import org.pnfolder.pnlibrary.core.link.ReceivedNetwork;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.data.PlanInfo;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuPermissions;
import org.pnfolder.pnlibrary.core.menu.style.MenuText;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.updates.catalog.screens.CatalogLabels;
import org.pnfolder.pnlibrary.core.updates.restart.ImportantNotices;

/** Menu home page with the main sections. */
public final class HomeScreens extends MenuScreens {
    private static final int NETWORK_SLOT = 37;
    private static final int SUPPORT_SLOT = 38;
    private static final int NOTICES_SLOT = 39;
    private static final int CHANNELS_SLOT = 40;
    private static final int RESTART_SLOT = 41;
    private static final int ROLLBACK_SLOT = 42;

    public HomeScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    public void home(MenuViewer player) {
        List<UpdateSnapshot> snapshots = snapshots();
        int pending = 0;
        int failing = 0;
        for (UpdateSnapshot snapshot : snapshots) {
            pending += CatalogLabels.PENDING.contains(snapshot.getState()) ? 1 : 0;
            failing += CatalogLabels.PROBLEMS.contains(snapshot.getState()) ? 1 : 0;
        }
        int errors = data().journalGroups("error", MenuConstants.JOURNAL_HOURS, MenuConstants.JOURNAL_LIMIT).size();
        int problems = failing + errors;
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Главная")).rows(MenuConstants.HOME_ROWS), MenuConstants.HOME_ROWS);
        summary(builder, pending, problems);
        sections(builder, pending, problems, errors);
        network(builder);
        notices(builder);
        channels(builder);
        builder.button(MenuConstants.HOME_CLOSE, MenuItems.closeItem(), go(MenuViewer::close));
        planButtons(builder, data().plan());
        show(player, builder.build());
    }

    private void summary(MenuBuilder builder, int pending, int problems) {
        String total = problems > 0 ? "✖ проблем: " + problems : pending > 0 ? "✦ к обновлению: " + pending : "✔ всё актуально";
        builder.slot(4, MenuItems.icon(Icons.OVERVIEW, "NETHER_STAR", PnLibraryIdentity.NAME + " " + data().version(), Style.lore(
            Collections.singletonList("Библиотека для плагинов pnFolder: обновления, настройки и поддержка."),
            Fact.list("Версия", data().version(), "Итог", total, "Плагинов", String.valueOf(connectedCount()),
                "Обновлений", String.valueOf(pending), "Проблем", String.valueOf(problems)))));
    }

    private void sections(MenuBuilder builder, int pending, int problems, int errors) {
        builder.button(11, MenuItems.section(Icons.PLUGINS, "BOOKSHELF", "Плагины", "Все плагины, подключённые к библиотеке.",
            new Fact("Всего", String.valueOf(connectedCount()))), go(who -> routes.plugins(who, Filter.ALL, 0)));
        builder.button(13, MenuItems.section(Icons.UPDATES, "GOLD_INGOT", "Обновления", "Плагины, для которых вышла новая версия.",
            new Fact("Доступно", String.valueOf(pending))), go(who -> routes.plugins(who, Filter.UPDATES, 0)));
        builder.button(15, MenuItems.section(Icons.PROBLEMS, "BARRIER", "Проблемы", "Ошибки проверки и блокировки обновлений.",
            new Fact("Найдено", String.valueOf(problems))), go(who -> routes.plugins(who, Filter.PROBLEMS, 0)));
        builder.button(29, MenuItems.section(Icons.HISTORY, "BOOK", "История", "Проверки, обновления, откаты и ошибки."),
            go(who -> routes.history(who, null, 0)));
        builder.button(30, MenuItems.section(Icons.PROBLEMS, "BOOK", "Журнал сервера",
            "Ошибки и предупреждения сервера за последние сутки.", new Fact("Ошибок", errors > 0 ? "✖ " + errors : "✔ нет")),
            go(who -> routes.journal(who, null, 0)));
        toolSections(builder);
        maintenanceSections(builder);
    }

    private void toolSections(MenuBuilder builder) {
        builder.button(32, MenuItems.section(Icons.CLOCK, "CLOCK", "Задачи плагинов", "Что плагины делают по расписанию и для чего.",
            new Fact("Всего", String.valueOf(data().tasks().size()))), go(who -> allowed(who, MenuConstants.TASKS,
            () -> routes.tasks(who, TaskPeriod.TODAY, TaskState.ALL, 0))));
        builder.button(28, MenuItems.section(Icons.TOOLS, "CHEST", "Модули плагинов", "Включить, выключить или перезапустить модули.",
            new Fact("Всего", String.valueOf(data().modules().size()))), go(who -> allowed(who, MenuPermissions.MODULES,
            () -> routes.modules(who, null, 0, routes::home))));
        builder.button(34, MenuItems.section(Icons.CHANGELOG, "BOOKSHELF", "Библиотеки плагинов",
            "Какие библиотеки подключили плагины и модули.", new Fact("Всего", String.valueOf(data().libraries().size()))),
            go(who -> routes.libraries(who, null, 0, routes::home)));
        builder.button(19, MenuItems.section(Icons.OVERVIEW, "HOPPER", "Диск плагинов",
            "Сколько места занимают плагины, настройки и библиотеки."),
            go(who -> allowed(who, MenuPermissions.MODULES, () -> routes.disk(who, 0, routes::home))));
        builder.button(31, MenuItems.section(Icons.UPDATES, "REDSTONE_TORCH", "События плагинов",
            "Как плагины общаются друг с другом: слушатели, вызовы, ошибки."),
            go(who -> allowed(who, MenuConstants.BUS, () -> routes.busHome(who))));
        builder.button(25, MenuItems.section(Icons.OVERVIEW, "COMPARATOR", "Статистика", "Задачи, события, ошибки и предупреждения за сутки."),
            go(who -> allowed(who, MenuConstants.STATS, () -> routes.stats(who))));
        builder.button(21, MenuItems.section(Icons.SETTINGS, "WRITABLE_BOOK", "Конфигурации",
            "Файлы настроек плагинов: включить, выключить, изменить.", new Fact("Файлов", String.valueOf(data().configs().size()))),
            go(who -> allowed(who, MenuConstants.CONFIGS, () -> routes.configs(who, 0))));
        builder.button(23, MenuItems.section(Icons.TOOLS, "NAME_TAG", "Плейсхолдеры", "Что плагины публикуют для текста и PlaceholderAPI."),
            go(who -> allowed(who, MenuConstants.PLACEHOLDERS, () -> routes.placeholders(who, 0))));
        builder.button(29, MenuItems.section(Icons.TOOLS, "COMMAND_BLOCK", "Действия",
            "Именованные действия, требования и функции плагинов."),
            go(who -> allowed(who, MenuConstants.ACTIONS, () -> routes.actions(who, 0))));
    }

    private void maintenanceSections(MenuBuilder builder) {
        builder.button(33, MenuItems.section(Icons.BACKUPS, "CHEST", "Резервные копии", "Откат к сохранённым версиям плагинов."),
            go(who -> routes.backups(who, 0)));
        builder.button(24, MenuItems.icon(Icons.CHECK, "COMPASS", "Проверить всё", Style.lore(
            Collections.singletonList("Спросить каталоги релизов прямо сейчас."), Collections.emptyList(), Style.left("проверить"))),
            go(routes::checkAll));
        builder.button(20, MenuItems.section(Icons.SETTINGS, "REDSTONE", "Настройки", "Пауза обновлений для всех плагинов сразу."),
            go(routes::settings));
        builder.button(22, MenuItems.section(Icons.TOOLS, "ANVIL", "Инструменты", "Статус сервера, отчёт для поддержки, валюты."),
            go(routes::tools));
        builder.button(SUPPORT_SLOT, MenuItems.icon(Icons.SUPPORT, "PAPER", "Поддержка", Style.lore(
            Collections.singletonList("Если возникла проблема, мы поможем."), Collections.emptyList(),
            Style.left("получить ссылку"))), go(routes::support));
    }

    private void network(MenuBuilder builder) {
        ReceivedNetwork received = host().getLink().getNetwork();
        if (remote() || received == null && !host().getLink().getEnabled()) {
            return;
        }
        String about = "Прокси и другие серверы сети: их плагины и обновления.";
        ItemSpec item = received == null
            ? MenuItems.section(Icons.OVERVIEW, "ENDER_EYE", "Сеть", about, new Fact("Данные", "ещё не пришли"))
            : MenuItems.section(Icons.OVERVIEW, "ENDER_EYE", "Сеть", about,
                new Fact("Серверов", String.valueOf(received.getReport().getServers().size() + 1)),
                new Fact("Данные", MenuText.ago(received.getReceivedAt())));
        builder.button(NETWORK_SLOT, item, go(who -> routes.network(who, 0)));
    }

    private void channels(MenuBuilder builder) {
        List<String> names = data().channels();
        if (names.isEmpty()) {
            return;
        }
        ItemSpec item = MenuItems.icon(Icons.OVERVIEW, "ENDER_PEARL", "Каналы прокси", Style.lore(
            Collections.singletonList("Каналы сообщений плагинов, открытые на прокси."),
            Fact.list("Открыто", String.valueOf(names.size())), Style.left("открыть")));
        builder.button(CHANNELS_SLOT, item, go(who -> allowed(who, MenuConstants.CHANNELS, () -> routes.channels(who, 0))));
    }

    private void notices(MenuBuilder builder) {
        if (remote()) {
            return;
        }
        int count = ImportantNotices.forServer(host().getLink(), Instant.now()).size();
        ItemSpec item = MenuItems.icon(Icons.RESTART, "BELL", "Важные объявления", Style.lore(
            Collections.singletonList("Что нужно сделать на прокси и серверах сети, например перезапустить."),
            Fact.list("Сейчас", count > 0 ? "✦ " + count : "✔ ничего"), Style.left("открыть")),
            count > 0 ? Tone.WARN : Tone.NORMAL);
        builder.button(NOTICES_SLOT, item, go(who -> allowed(who, MenuPermissions.UPDATES_RESTART, () -> routes.notices(who, 0))));
    }

    private void planButtons(MenuBuilder builder, PlanInfo plan) {
        if (plan != null && plan.getState() == UpdateState.UPDATE_STAGED) {
            ItemSpec restart = MenuItems.icon(Icons.RESTART, "DIAMOND", "Перезапуск", Style.lore(
                Collections.singletonList("Обновления подготовлены."), Collections.emptyList(), Style.left("подтвердить в чате")));
            builder.button(RESTART_SLOT, restart, go(who -> {
                who.close();
                runCommand(who, "pn update restart");
            }));
        }
        if (plan != null && plan.hasChanges()) {
            ItemSpec rollback = MenuItems.icon(Icons.ROLLBACK, "TNT", "Откатить план", Style.lore(
                Collections.singletonList("Откатит все связанные плагины плана."), Collections.emptyList(),
                Style.left("подтвердить в чате")), Tone.DANGER);
            builder.button(ROLLBACK_SLOT, rollback, go(who -> {
                who.close();
                runCommand(who, "pn update rollback");
            }));
        }
    }
}
