package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.data.MissingPlugins;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** The plugins other plugins need and the server lacks, and the switch for downloading them. */
public final class MissingPluginScreens extends MenuScreens {
    public MissingPluginScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    /** The plugins other plugins need and the server lacks: who asks, what, from where; a click downloads a group. */
    public void missingPlugins(MenuViewer player, int page) {
        List<Row> rows = new ArrayList<>();
        for (MissingPlugins group : data().missingDependencies()) {
            List<String> lines = new ArrayList<>(group.getLines());
            for (String link : group.getLinks().subList(0, Math.min(4, group.getLinks().size()))) {
                lines.add("  " + link);
            }
            if (!group.isInstallable()) {
                lines.add("Скачайте по ссылкам и положите в plugins.");
            }
            List<String> lore = Style.lore(lines, Fact.list("Код", group.getCode()), group.isInstallable() ? Style.left("скачать") : "");
            rows.add(new Row(MenuItems.icon(null, group.isInstallable() ? "HOPPER" : "BARRIER", "Нужно для " + group.getOwner(), lore,
                group.isInstallable() ? Tone.NORMAL : Tone.DANGER), go(who -> {
                    if (group.isInstallable()) {
                        allowed(who, MenuConstants.SETTINGS, () -> install(who, group, page));
                    }
                })));
        }
        list(player, "Недостающие плагины", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> missingPlugins(who, to),
            routes::settings, "Всё нужное установлено");
    }

    private void install(MenuViewer who, MissingPlugins group, int page) {
        tell(who, ChatTone.INFO, "Скачиваю…");
        data().installDependencies(group.getCode()).whenComplete((text, error) -> sync(() -> {
            boolean ok = error == null && text.startsWith("Готово");
            tell(who, ok ? ChatTone.SUCCESS : ChatTone.FAILURE, text == null ? "Не вышло." : text);
            missingPlugins(who, page);
        }));
    }

    /** The button of the settings screen about missing plugins. */
    public void buttons(MenuBuilder builder) {
        List<MissingPlugins> missing = data().missingDependencies();
        if (!missing.isEmpty()) {
            builder.button(14, MenuItems.icon(Icons.SETTINGS, "CHEST", "Что не хватает", Style.lore(CoreFormatting.wrapText(
                "Плагины, которые просят другие плагины, а на сервере их нет. Там же можно скачать.", CoreFormatting.WRAP_WIDTH, 3),
                Fact.list("Групп", String.valueOf(missing.size())), Style.left("открыть")), Tone.DANGER),
                go(who -> allowed(who, MenuConstants.SETTINGS, () -> missingPlugins(who, 0))));
        }
    }
}
