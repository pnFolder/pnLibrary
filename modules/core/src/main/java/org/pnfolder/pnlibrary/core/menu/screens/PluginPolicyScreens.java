package org.pnfolder.pnlibrary.core.menu.screens;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.HoldOption;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuText;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;

/** Menu screens for per-plugin update policies: the Java build and the pause. */
public final class PluginPolicyScreens extends MenuScreens {
    private static final int MAX_BUILDS = 7;

    public PluginPolicyScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    /** The Java build choice for a release that has several builds, for example one for Java 8 and one for Java 17. */
    public void javaBuilds(MenuViewer player, String product) {
        UpdateSnapshot snapshot = data().snapshot(product);
        if (snapshot == null) {
            routes.home(player);
            return;
        }
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Сборка Java " + product)).rows(MenuConstants.SMALL_ROWS), MenuConstants.SMALL_ROWS);
        List<Integer> builds = snapshot.getJavaBuilds();
        for (int index = 0; index < Math.min(builds.size(), MAX_BUILDS); index++) {
            Integer java = builds.get(index);
            boolean chosen = java.equals(snapshot.getJavaBuild());
            boolean recommended = java.equals(snapshot.getRecommendedJava());
            List<Fact> notes = new ArrayList<>();
            if (chosen) {
                notes.add(new Fact("Состояние", "выбрана сейчас"));
            }
            if (recommended) {
                notes.add(new Fact("Рекомендуется", snapshot.getJavaBuild() == null ? "используется" : "подходит этому серверу"));
            }
            notes.add(new Fact("На сервере", "Java " + snapshot.getCurrentJava()));
            List<String> lore = chosen
                ? Style.lore(Collections.singletonList("Скачать сборку для Java " + java + "."), notes)
                : Style.lore(Collections.singletonList("Скачать сборку для Java " + java + "."), notes, Style.left("выбрать"));
            builder.button(10 + index, MenuItems.icon(Icons.SETTINGS, "COMPARATOR", "Java " + java, lore,
                chosen || recommended && snapshot.getJavaBuild() == null ? Tone.GOOD : Tone.NORMAL),
                go(who -> allowed(who, MenuConstants.SETTINGS, () -> chooseJava(who, product, java))));
        }
        builder.button(16, MenuItems.icon(Icons.RESET, "BARRIER", "Подбирать самому", Style.lore(Collections.singletonList(
            "Сборка, как у установленной версии, иначе самая новая из подходящих."), Collections.emptyList(),
            Style.left("сбросить")), Tone.MUTED), go(who -> allowed(who, MenuConstants.SETTINGS, () -> chooseJava(who, product, null))));
        builder.button(MenuConstants.SMALL_BACK, MenuItems.backItem(), go(who -> routes.card(who, product)));
        show(player, builder.build());
    }

    private void chooseJava(MenuViewer player, String product, @Nullable Integer java) {
        data().setJavaBuild(product, java, player.getActor()).whenComplete((done, error) -> sync(() -> {
            if (error != null) {
                tell(player, ChatTone.FAILURE, "Не удалось выбрать сборку Java.", Throwables.rootMessage(error));
            } else {
                tell(player, ChatTone.SUCCESS, java == null ? "Сборка Java " + product + " подбирается сама."
                    : "Сборка Java " + product + ": " + java + ".");
            }
            routes.card(player, product);
        }));
    }

    private static ItemSpec holdSummary(@Nullable Duration remaining) {
        return MenuItems.icon(Icons.CLOCK, "CLOCK", "Пауза сейчас", Style.lore(Collections.singletonList(
            "Пока пауза идёт, обновления не предлагаются и не скачиваются."),
            Fact.list("Состояние", remaining == null ? "нет паузы" : "‖ осталось " + MenuText.timeLeft(remaining))),
            "WATCH", remaining != null ? Tone.WARN : Tone.NORMAL);
    }

    private static ItemSpec holdChoice(String label, @Nullable Duration selectedRemaining) {
        List<Fact> note = selectedRemaining == null ? Collections.emptyList()
            : Fact.list("Выбрано", "● осталось " + MenuText.timeLeft(selectedRemaining));
        String action = selectedRemaining != null ? "начать срок заново" : "приостановить";
        return MenuItems.icon(Icons.CLOCK, "CLOCK", label, Style.lore(Collections.singletonList(
            "Приостановить обновления на этот срок."), note, Style.left(action)), "WATCH",
            selectedRemaining != null ? Tone.GOOD : Tone.NORMAL);
    }

    public void holds(MenuViewer player, String product) {
        Instant until = data().heldUntil(product);
        Duration remaining = until != null && until.isAfter(Instant.now()) ? Duration.between(Instant.now(), until) : null;
        int chosen = remaining == null ? -1 : holdIndex(remaining);
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Пауза " + product)).rows(MenuConstants.SMALL_ROWS), MenuConstants.SMALL_ROWS);
        builder.slot(4, holdSummary(remaining));
        for (int index = 0; index < MenuConstants.HOLDS.size(); index++) {
            HoldOption option = MenuConstants.HOLDS.get(index);
            builder.button(10 + index, holdChoice(option.getLabel(), index == chosen ? remaining : null),
                go(who -> allowed(who, MenuConstants.SETTINGS, () -> chooseHold(who, product, option.getDuration()))));
        }
        if (remaining != null) {
            builder.button(16, MenuItems.icon(Icons.RESET, "REDSTONE", "Снять паузу", Style.lore(
                Collections.singletonList("Вернуть обновления сейчас."), Collections.emptyList(), Style.left("снять")), Tone.GOOD),
                go(who -> allowed(who, MenuConstants.SETTINGS, () -> chooseHold(who, product, null))));
        }
        builder.button(MenuConstants.SMALL_BACK, MenuItems.backItem(), go(who -> routes.card(who, product)));
        show(player, builder.build());
    }

    private static int holdIndex(Duration remaining) {
        for (int index = 0; index < MenuConstants.HOLDS.size(); index++) {
            if (MenuConstants.HOLDS.get(index).getDuration().compareTo(remaining) >= 0) {
                return index;
            }
        }
        return MenuConstants.HOLDS.size() - 1;
    }

    private void chooseHold(MenuViewer player, String product, @Nullable Duration duration) {
        data().setHeld(product, duration, player.getActor()).whenComplete((done, error) -> sync(() -> {
            if (error != null) {
                tell(player, ChatTone.FAILURE, "Не удалось изменить паузу.", Throwables.rootMessage(error));
            } else {
                tell(player, ChatTone.SUCCESS, duration == null ? "Пауза " + product + " снята." : "Обновления " + product + " приостановлены.");
            }
            routes.card(player, product);
        }));
    }
}
