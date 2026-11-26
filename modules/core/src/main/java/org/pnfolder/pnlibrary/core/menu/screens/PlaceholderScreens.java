package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.placeholders.UsageVariant;
import org.pnfolder.pnlibrary.core.placeholders.UsageVariants;

/** Menu screens for registered placeholders. */
public final class PlaceholderScreens extends MenuScreens {
    public PlaceholderScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    /** Every placeholder with its type, format and PlaceholderAPI names; checking a value stays in {@code /pn placeholders test}. */
    public void placeholders(MenuViewer player, int page) {
        List<Row> rows = new ArrayList<>();
        for (PlaceholderInfo info : data().placeholders()) {
            List<Fact> parameters = new ArrayList<>();
            parameters.add(new Fact("Тип", info.getType()));
            if (!info.getDescription().isEmpty()) {
                parameters.add(new Fact("Описание", info.getDescription()));
            }
            parameters.add(new Fact("Владелец", info.getOwner()));
            parameters.add(new Fact("Видимость", info.getVisibility().name().toLowerCase(Locale.ROOT)));
            if (info.getFormat() != null) {
                parameters.add(new Fact("Формат", info.getFormat()));
            }
            if (!info.getPublished().isEmpty()) {
                List<String> names = new ArrayList<>();
                for (String name : info.getPublished()) {
                    names.add("%" + name + "%");
                }
                parameters.add(new Fact("PlaceholderAPI", String.join(", ", names)));
            }
            for (UsageVariant variant : UsageVariants.of(info)) {
                parameters.add(new Fact(variant.getLabel(), variant.getText()));
            }
            rows.add(new Row(MenuItems.icon(null, "NAME_TAG", "{" + info.getAddress() + "}",
                Style.lore(Collections.<String>emptyList(), parameters)), go(who -> { })));
        }
        list(player, "Плейсхолдеры", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> placeholders(who, to), routes::home,
            "Плейсхолдеров нет");
    }
}
