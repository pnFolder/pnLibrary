package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot;

/** Menu screens with usage statistics. */
public final class StatsScreens extends MenuScreens {
    public StatsScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    public void stats(MenuViewer player) {
        StatsSnapshot now = data().stats();
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Статистика")).rows(MenuConstants.DETAIL_ROWS), MenuConstants.DETAIL_ROWS);
        builder.slot(4, overview(now));
        builder.slot(20, tasks(now));
        builder.slot(22, events(now));
        builder.slot(24, errors(now));
        builder.button(MenuConstants.DETAIL_BACK, MenuItems.backItem(), go(routes::home));
        show(player, builder.build());
    }

    private static ItemSpec overview(StatsSnapshot now) {
        return MenuItems.icon(Icons.OVERVIEW, "NETHER_STAR", "Сервер сейчас", Style.lore(
            Collections.singletonList("Числа без имён игроков и текстов: только счётчики и плагины."),
            Fact.list("Задач активно", String.valueOf(now.getTasksActive()), "Ошибок за сутки", String.valueOf(now.getErrors24h()))));
    }

    private static ItemSpec tasks(StatsSnapshot now) {
        return MenuItems.icon(Icons.CLOCK, "CLOCK", "Задачи", Style.lore(
            Collections.singletonList("Что плагины делают по расписанию."),
            Fact.list("Активно", String.valueOf(now.getTasksActive()), "С ошибкой", String.valueOf(now.getTasksFailed()),
                "Запусков", String.valueOf(now.getTaskRuns()), "Пропущено", String.valueOf(now.getTaskSkipped()))),
            now.getTasksFailed() > 0 ? Tone.DANGER : Tone.NORMAL);
    }

    private static ItemSpec events(StatsSnapshot now) {
        return MenuItems.icon(Icons.UPDATES, "REDSTONE_TORCH", "События", Style.lore(
            Collections.singletonList("Как плагины общаются друг с другом."),
            Fact.list("Вызовов", String.valueOf(now.getEventsCalled()), "Отменено", String.valueOf(now.getEventsDenied()))));
    }

    private static ItemSpec errors(StatsSnapshot now) {
        List<Fact> facts = Fact.list("Ошибок", String.valueOf(now.getErrors24h()), "Предупреждений", String.valueOf(now.getWarnings24h()));
        for (org.pnfolder.pnlibrary.common.lang.Pair<String, Integer> source : now.getErrorSources()) {
            facts.add(new Fact(source.getFirst(), String.valueOf(source.getSecond())));
        }
        return MenuItems.icon(Icons.PROBLEMS, "BARRIER", "Ошибки за сутки", Style.lore(
            Arrays.asList(now.getErrors24h() == 0 ? "Ошибок нет." : "Больше всего ошибок у:"), facts),
            now.getErrors24h() > 0 ? Tone.DANGER : Tone.NORMAL);
    }
}
