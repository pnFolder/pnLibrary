package org.pnfolder.pnlibrary.core.menu;

import org.pnfolder.pnlibrary.core.menu.session.ClickType;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.click;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.plain;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.slotNamed;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.pnfolder.pnlibrary.core.menu.data.MenuData;
import org.pnfolder.pnlibrary.core.menu.data.SettingOutcome;
import org.pnfolder.pnlibrary.core.menu.data.SettingRow;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.settings.ChoiceSource;
import org.pnfolder.pnlibrary.core.settings.LibrarySettings;
import org.pnfolder.pnlibrary.core.settings.SettingApply;
import org.pnfolder.pnlibrary.core.settings.SettingSpec;
import org.pnfolder.pnlibrary.core.settings.SettingsRegistry;

/** What the library settings menu tests share: the settings rows, a menu data stand-in and the way to the zone list. */
abstract class LibrarySettingsMenuSupport {
    protected final List<List<String>> changes = new ArrayList<>();

    protected static List<SettingRow> rows(Map<String, String> current) {
        List<SettingRow> rows = new ArrayList<>();
        for (SettingSpec spec : SettingsRegistry.getAll()) {
            String value = current.containsKey(spec.getKey()) ? current.get(spec.getKey()) : spec.valueIn(new LibrarySettings());
            rows.add(new SettingRow(spec.getKey(), spec.getTitle(), spec.getKind().name(), spec.getDescription(), value,
                spec.getDefault(), !value.equals(spec.getDefault()), spec.range(), spec.getExamples(),
                spec.getApplies() == SettingApply.LIVE, spec.getChoices() == null ? "" : spec.getChoices().getId()));
        }
        return rows;
    }

    protected static List<SettingRow> rows() {
        return rows(Collections.emptyMap());
    }

    protected static Map<String, String> with(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    protected MenuData data(final List<SettingRow> settings) {
        return (MenuData) Proxy.newProxyInstance(MenuData.class.getClassLoader(), new Class<?>[] {MenuData.class}, (proxy, method, args) -> {
            Class<?> type = method.getReturnType();
            if (method.getName().equals("settings")) {
                return settings;
            }
            if (method.getName().equals("changeSetting")) {
                changes.add(Arrays.asList((String) args[0], (String) args[1]));
                return new SettingOutcome(true, "ok");
            }
            if (type == List.class) {
                return Collections.emptyList();
            }
            if (type == String.class) {
                return "1.0";
            }
            if (type == int.class) {
                return 0;
            }
            return type == boolean.class ? (Object) false : null;
        });
    }

    protected Menu settingsScreen(TestMenuViewer viewer, List<SettingRow> settings) {
        new LibraryMenu(data(settings), new ImmediateHost()).home(viewer);
        click(viewer, last(viewer), 20);
        click(viewer, last(viewer), 22);
        return last(viewer);
    }

    protected static Menu last(TestMenuViewer viewer) {
        return viewer.opened.get(viewer.opened.size() - 1);
    }

    protected static String text(List<String> lore) {
        return lore.stream().map(MenuTestSupport::plain).collect(Collectors.joining("\n"));
    }

    protected Menu zoneList(TestMenuViewer viewer, String current) {
        Menu screen = settingsScreen(viewer, rows(with("time-zone", current)));
        click(viewer, screen, slotNamed(screen, "Часовой пояс"), ClickType.SHIFT_LEFT);
        return last(viewer);
    }

    protected Menu zoneList(TestMenuViewer viewer) {
        return zoneList(viewer, "Europe/Berlin");
    }

    protected static int pages() {
        return (ChoiceSource.ZONES.getAll().size() - 1) / MenuConstants.PAGE_SIZE + 1;
    }
}
