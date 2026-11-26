package org.pnfolder.pnlibrary.core.guide;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/** The commands and permissions of one part of the library, for the command guide. */
final class MenuGuide {
    private MenuGuide() {
    }

    /** The section of the guide. */
    static @NotNull GuideSection section() {
        return new GuideSection(
            "Меню и валюты",
            Arrays.asList(
                GuideEntry.of(
                    "/pn gui",
                    "pnlibrary.updates.view",
                    "Меню в инвентаре: плагины, обновления, история, ошибки, настройки.")
                    .orAdmin()
                    .scope(GuideScope.GAME_SERVER)
                    .note("Только игрок: в консоли нет инвентаря."),
                GuideEntry.of(
                    "/pn money list",
                    "pnlibrary.currency.list",
                    "Список валют."),
                GuideEntry.of(
                    "/pn money <валюта> <balance|history|add|take|set|reset|pay> …",
                    "pnlibrary.currency.<валюта>.<действие>",
                    "Баланс, история, начисление, списание, установка, обнуление и перевод игроку.")
                    .scope(GuideScope.GAME_SERVER)
                    .note(
                        "Право своё для каждой валюты и действия; плагин валюты может задать другое начало через " +
                            "permissionPrefix."),
                GuideEntry.of(
                    "/pn money <confirm|cancel> <код>",
                    null,
                    "Подтвердить или отменить опасную операцию с валютой.")
                    .scope(GuideScope.GAME_SERVER)
                    .note("Только консоль.")));
    }
}
