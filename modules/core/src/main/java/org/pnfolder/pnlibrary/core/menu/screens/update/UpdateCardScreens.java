package org.pnfolder.pnlibrary.core.menu.screens.update;

import org.pnfolder.pnlibrary.core.updates.catalog.UpdateStatusMessages;
import org.pnfolder.pnlibrary.core.menu.screens.PluginPolicyScreens;
import org.pnfolder.pnlibrary.core.menu.screens.Filter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.session.MenuClickHandler;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.data.ModuleInfo;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuPermissions;
import org.pnfolder.pnlibrary.core.menu.style.MenuText;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;

/** The card of one plugin: update actions, pause, releases and links to its history, modules and libraries. */
final class UpdateCardScreens extends MenuScreens {
    private static final int CARD_ROWS = 4;
    private static final int CARD_BACK = 31;

    private final UpdateScreens updates;
    private final PluginPolicyScreens policy;

    UpdateCardScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes, UpdateScreens updates) {
        super(session, routes);
        this.updates = updates;
        this.policy = new PluginPolicyScreens(session, routes);
    }

    void card(MenuViewer player, String product) {
        UpdateSnapshot snapshot = data().snapshot(product);
        if (snapshot == null) {
            routes.home(player);
            return;
        }
        MenuBuilder builder = MenuItems.menuFrame(Menus.chest(MenuItems.menuTitle(snapshot.getProduct())).rows(CARD_ROWS), CARD_ROWS);
        builder.slot(4, updates.plate(snapshot));
        actions(builder, snapshot);
        builder.slot(13, MenuItems.icon(Icons.LOCKED, "BARRIER", "Канал обновлений", Style.lore(
            Collections.singletonList("Сейчас работает только канал dev, остальные пока недоступны."),
            Fact.list("Канал", snapshot.getChannel().name().toLowerCase(Locale.ROOT))), Tone.MUTED));
        builder.button(14, holdItem(snapshot), go(who -> policy.holds(who, snapshot.getProduct())));
        builder.button(15, MenuItems.section(Icons.RELEASES, "BOOKSHELF", "Релизы", "Известные версии плагина.",
            new Fact("Найдено", String.valueOf(snapshot.getAvailableReleases().size()))), go(who -> releases(who, snapshot.getProduct(), 0)));
        builder.button(16, MenuItems.section(Icons.HISTORY, "BOOK", "История", "Записи только этого плагина."),
            go(who -> routes.history(who, snapshot.getProduct(), 0)));
        links(builder, snapshot);
        builder.button(CARD_BACK, MenuItems.backItem(), go(who -> routes.plugins(who, Filter.ALL, 0)));
        show(player, builder.build());
    }

    private void links(MenuBuilder builder, UpdateSnapshot snapshot) {
        String product = snapshot.getProduct();
        if (snapshot.getLatestChangelogUrl() != null) {
            builder.button(22, MenuItems.icon(Icons.CHANGELOG, "WRITTEN_BOOK", "Список изменений", Style.lore(
                Collections.singletonList("Что нового в последней версии."), Collections.emptyList(),
                Style.left("получить ссылку"))), go(who -> {
                    who.close();
                    tell(who, ChatTone.INFO, "Список изменений " + product, snapshot.getLatestChangelogUrl());
                }));
        }
        if (snapshot.getJavaBuilds().size() > 1) {
            Integer now = snapshot.getJavaBuild() != null ? snapshot.getJavaBuild() : snapshot.getRecommendedJava();
            String shown = now == null ? "подбор" : "Java " + now + (snapshot.getJavaBuild() == null ? " (подбор)" : "");
            builder.button(23, MenuItems.section(Icons.SETTINGS, "COMPARATOR", "Сборка Java",
                "У релиза несколько сборок: выберите под вашу Java.", new Fact("Сейчас", shown)), go(who -> policy.javaBuilds(who, product)));
        }
        int modules = 0;
        for (ModuleInfo info : data().modules()) {
            modules += info.getKey().toLowerCase(Locale.ROOT).startsWith(product.toLowerCase(Locale.ROOT) + ":") ? 1 : 0;
        }
        if (modules > 0) {
            builder.button(24, MenuItems.section(Icons.TOOLS, "CHEST", "Модули", "Включить, выключить или перезапустить модули плагина.",
                new Fact("Модулей", String.valueOf(modules))), go(who -> allowed(who, MenuPermissions.MODULES,
                () -> routes.modules(who, product, 0, v -> card(v, product)))));
        }
        int libraries = 0;
        for (org.pnfolder.pnlibrary.core.commands.modules.LibraryRow row : data().libraries()) {
            libraries += row.belongsTo(product) ? 1 : 0;
        }
        if (libraries > 0) {
            builder.button(25, MenuItems.section(Icons.CHANGELOG, "BOOKSHELF", "Библиотеки",
                "Что плагин и его модули подключили для работы.", new Fact("Всего", String.valueOf(libraries))),
                go(who -> routes.libraries(who, product, 0, v -> card(v, product))));
        }
    }

    private void actions(MenuBuilder builder, UpdateSnapshot snapshot) {
        ItemSpec update = snapshot.getState() == UpdateState.UPDATE_AVAILABLE
            ? MenuItems.icon(null, "EMERALD", "Обновить", Style.lore(java.util.Arrays.asList(
                "Подготовить версию " + snapshot.getLatestVersion() + ".", "Применится после перезапуска."),
                Collections.emptyList(), Style.left("подготовить")))
            : MenuItems.icon(null, "COAL", "Обновить", Style.lore(Collections.singletonList("Сейчас нет доступного обновления."),
                Collections.emptyList()), Tone.MUTED);
        builder.button(10, update, go(who -> allowed(who, MenuPermissions.UPDATES_DOWNLOAD, () -> {
            who.close();
            runCommand(who, "pn update " + snapshot.getProduct());
        })));
        builder.button(11, MenuItems.icon(Icons.CHECK, "COMPASS", "Проверить", Style.lore(
            Collections.singletonList("Проверить обновления этого плагина."), Collections.emptyList(), Style.left("проверить"))),
            go(who -> updates.checkOne(who, snapshot.getProduct())));
        if (PnLibraryIdentity.PRODUCT_ID.equalsIgnoreCase(snapshot.getProduct())) {
            builder.button(12, MenuItems.icon(null, "BLAZE_POWDER", "Обновить pnLibrary принудительно", Style.lore(
                java.util.Arrays.asList("Подготовить только pnLibrary, даже если план заблокирован.",
                    "Применится после перезапуска."), Collections.emptyList(), Style.left("подготовить")), Tone.WARN),
                go(who -> allowed(who, MenuPermissions.UPDATES_DOWNLOAD, () -> updates.forceLibrary(who))));
        }
    }

    private ItemSpec holdItem(UpdateSnapshot snapshot) {
        boolean held = snapshot.getState() == UpdateState.FROZEN;
        List<String> lore = Style.lore(Collections.singletonList(held ? "Обновления сейчас приостановлены." : "Приостановить обновления на время."),
            Collections.emptyList(), Style.left(held ? "изменить или снять" : "выбрать срок"));
        return MenuItems.icon(Icons.CLOCK, "CLOCK", "Пауза", lore, "WATCH", held ? Tone.WARN : Tone.NORMAL);
    }

    private void releases(MenuViewer player, String product, int page) {
        UpdateSnapshot snapshot = data().snapshot(product);
        if (snapshot == null) {
            routes.home(player);
            return;
        }
        List<Row> rows = new ArrayList<>();
        for (ReleaseSummary release : snapshot.getAvailableReleases()) {
            boolean current = release.getVersion().equals(snapshot.getCurrentVersion());
            String link = release.getChangelogUrl();
            List<Fact> parameters = Fact.list("Канал", release.getChannel().name().toLowerCase(Locale.ROOT));
            if (release.getPublishedAt() != null) {
                parameters.add(new Fact("Выпущен", MenuText.clockTime(release.getPublishedAt())));
            }
            if (release.getApi() != null) {
                parameters.add(new Fact("API", release.getApi().getMinimum() + "–" + release.getApi().getMaximum()));
            }
            String risk = UpdateStatusMessages.riskNote(snapshot, release.getVersion());
            if (risk != null) {
                parameters.add(new Fact("Внимание", risk));
            }
            if (current) {
                parameters.add(new Fact("Состояние", "установлена сейчас"));
            }
            List<String> lore = link != null
                ? Style.lore(Collections.emptyList(), parameters, Style.left("получить список изменений"))
                : Style.lore(Collections.emptyList(), parameters);
            MenuClickHandler click = link == null ? null : go(who -> {
                who.close();
                tell(who, ChatTone.INFO, "Список изменений " + release.getVersion(), link);
            });
            rows.add(new Row(MenuItems.icon(null, current ? "EMERALD" : "PAPER", release.getVersion(), lore,
                current ? Tone.GOOD : Tone.NORMAL), click));
        }
        list(player, "Релизы " + product, rows, page, MenuConstants.PAGE_SIZE, (who, to) -> releases(who, product, to),
            who -> card(who, product), "Релизов пока нет");
    }
}
