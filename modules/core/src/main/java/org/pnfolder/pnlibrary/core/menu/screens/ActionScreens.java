package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.placeholders.UsageVariant;
import org.pnfolder.pnlibrary.core.scenario.DefinitionUsage;
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo;

/** Menu screens for the named actions, requirements and functions that plugins defined. */
public final class ActionScreens extends MenuScreens {
    public ActionScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    /** Every definition with its owner, visibility and the ways to write it; the same as {@code /pn actions info}. */
    public void actions(MenuViewer player, int page) {
        List<Row> rows = new ArrayList<>();
        for (ScenarioDefinitionInfo info : data().scenarioDefinitions()) {
            List<Fact> parameters = Fact.list("Вид", info.getKind(), "Владелец", info.getOwner(), "Видимость", info.getVisibility());
            for (UsageVariant variant : DefinitionUsage.of(info)) {
                parameters.add(new Fact(variant.getLabel(), variant.getText()));
            }
            rows.add(new Row(MenuItems.icon(null, "COMMAND_BLOCK", info.getName(),
                Style.lore(Collections.emptyList(), parameters)), go(who -> { })));
        }
        list(player, "Действия", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> actions(who, to), routes::home, "Действий нет");
    }
}
