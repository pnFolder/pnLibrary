package org.pnfolder.pnlibrary.core.menu.screens.config;

import org.pnfolder.pnlibrary.core.menu.screens.MissingPluginScreens;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.data.SettingRow;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.HoldOption;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuPermissions;
import org.pnfolder.pnlibrary.core.menu.style.MenuText;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;
import org.pnfolder.pnlibrary.updates.policy.UpdateDefaults;

/** Menu screens for library settings. */
public final class SettingsScreens extends MenuScreens {
    private static final int SUPPORT_TOOL_SLOT = 16;
    private static final int CYCLE_HOLDS = 3;

    private final MissingPluginScreens missing;

    public SettingsScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
        this.missing = new MissingPluginScreens(session, routes);
    }

    public void missingPlugins(MenuViewer player, int page) {
        missing.missingPlugins(player, page);
    }

    public void settings(MenuViewer player) {
        List<UpdateSnapshot> all = snapshots();
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Настройки")).rows(MenuConstants.SMALL_ROWS + 1), MenuConstants.SMALL_ROWS + 1);
        builder.slot(4, MenuItems.icon(Icons.SETTINGS, "REDSTONE", "Как это работает", Style.lore(
            Collections.singletonList("Проверка и сообщения включены всегда, отключить библиотеку нельзя."),
            Fact.list("Проверка", "каждые " + checkInterval(), "Консоль", "сообщения всегда", "Канал", "только dev"))));
        missing.buttons(builder);
        pauseForAll(builder, all);
        builder.button(MenuConstants.SMALL_BACK, MenuItems.icon(Icons.SETTINGS, "WRITABLE_BOOK", "Параметры библиотеки", Style.lore(
            CoreFormatting.wrapText("Приватность отчётов, часовой пояс, загрузки файлов, время проверки обновлений. То же делает /pn settings.",
                CoreFormatting.WRAP_WIDTH, 4), Collections.emptyList(), Style.left("открыть"))),
            go(who -> allowed(who, MenuPermissions.LIBRARY_SETTINGS, () -> routes.librarySettings(who, 0))));
        builder.button(MenuConstants.SMALL_BACK + 9, MenuItems.backItem(), go(routes::home));
        show(player, builder.build());
    }

    /** How often the catalogs are asked, as this server has it set. */
    private String checkInterval() {
        for (SettingRow row : data().settings()) {
            if (row.getKey().equals("updates.check-interval")) {
                return row.getCurrent();
            }
        }
        return UpdateDefaults.CHECK_INTERVAL.toHours() + "ч";
    }

    private void pauseForAll(MenuBuilder builder, List<UpdateSnapshot> all) {
        Duration longest = null;
        int paused = 0;
        for (UpdateSnapshot snapshot : all) {
            Instant until = data().heldUntil(snapshot.getProduct());
            if (until != null && until.isAfter(Instant.now())) {
                paused++;
                Duration left = Duration.between(Instant.now(), until);
                longest = longest == null || left.compareTo(longest) > 0 ? left : longest;
            }
        }
        List<HoldOption> cycle = MenuConstants.HOLDS.subList(0, CYCLE_HOLDS);
        List<String> choices = new ArrayList<>();
        choices.add("нет паузы");
        for (HoldOption option : cycle) {
            choices.add("на " + option.getLabel());
        }
        int selected = 0;
        if (longest != null) {
            int found = CYCLE_HOLDS - 1;
            for (int index = 0; index < cycle.size(); index++) {
                if (cycle.get(index).getDuration().compareTo(longest) >= 0) {
                    found = index;
                    break;
                }
            }
            selected = 1 + found;
        }
        List<Fact> note = new ArrayList<>();
        if (longest != null) {
            note.add(new Fact("Осталось", MenuText.timeLeft(longest)));
        }
        if (paused > 0) {
            note.add(new Fact("На паузе", paused + " из " + all.size()));
        }
        List<String> extra = new ArrayList<>(Style.choices(choices, selected));
        extra.add("");
        extra.add(Style.description("Пауза касается плагинов с обновлениями: " + all.size() + "."));
        int unregistered = connectedCount() - all.size();
        if (unregistered > 0) {
            extra.add(Style.description("Без регистрации обновлений: " + unregistered + ", их пауза не касается."));
        }
        int current = selected;
        builder.button(15, MenuItems.icon(Icons.CLOCK, "CLOCK", "Пауза для всех", Style.lore(Arrays.asList(
            "Все плагины на время не обновляются.", "Срок заменяет личные паузы плагинов; «нет паузы» снимает и их."),
            note, extra, Style.left("следующий срок")), "WATCH", paused > 0 ? Tone.WARN : Tone.NORMAL),
            go(who -> allowed(who, MenuConstants.SETTINGS, () -> cyclePause(who, (current + 1) % choices.size()))));
    }

    private void cyclePause(MenuViewer who, int nextIndex) {
        List<HoldOption> cycle = MenuConstants.HOLDS.subList(0, CYCLE_HOLDS);
        HoldOption option = nextIndex >= 1 && nextIndex - 1 < cycle.size() ? cycle.get(nextIndex - 1) : null;
        String done = option == null ? "Все паузы сняты." : "Обновления всех плагинов приостановлены: " + option.getLabel() + ".";
        routes.everyone(who, done, snapshot -> routes.hold(snapshot, option == null ? null : option.getDuration(), who));
    }

    public void tools(MenuViewer player) {
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Инструменты")).rows(MenuConstants.SMALL_ROWS + 1), MenuConstants.SMALL_ROWS + 1);
        String[][] entries = {
            {Icons.CHECK, "COMPASS", "Статус сервера", "Версии сервера и всех плагинов.", "pn status"},
            {Icons.CHANGELOG, "WRITTEN_BOOK", "Отчёт для поддержки", "Зашифрованный отчёт, который можно отправить нам.", "pn debug"},
            {Icons.RELEASES, "GOLD_NUGGET", "Валюты", "Валюты и балансы игроков.", "pn money list"}};
        for (int index = 0; index < entries.length; index++) {
            String[] entry = entries[index];
            builder.button(10 + index, MenuItems.icon(entry[0], entry[1], entry[2], Style.lore(Collections.singletonList(entry[3]),
                Fact.list("Команда", "/" + entry[4]), Style.left("выполнить"))), go(who -> {
                    who.close();
                    runCommand(who, entry[4]);
                }));
        }
        builder.button(SUPPORT_TOOL_SLOT, MenuItems.icon(Icons.SUPPORT, "PAPER", "Поддержка", Style.lore(
            Collections.singletonList("Ссылка на помощь."), Collections.emptyList(), Style.left("получить"))), go(this::support));
        builder.button(MenuConstants.SMALL_BACK + 9, MenuItems.backItem(), go(routes::home));
        show(player, builder.build());
    }

    public void support(MenuViewer player) {
        player.close();
        deliver(player, UpdateCatalogView.support(PnLibraryIdentity.SUPPORT_URL, MenuConstants.ROUTES));
    }
}
