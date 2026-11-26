package org.pnfolder.pnlibrary.core.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskPeriod;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskState;
import org.pnfolder.pnlibrary.core.menu.data.MenuData;
import org.pnfolder.pnlibrary.core.menu.screens.Filter;
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot;

/** Opens every list screen over a server that has nothing, so a screen that cannot show emptiness fails here. */
class MenuScreensSmokeTest {
    private static MenuData emptyData() {
        return (MenuData) Proxy.newProxyInstance(MenuData.class.getClassLoader(), new Class<?>[] {MenuData.class}, (proxy, method, args) -> {
            Class<?> type = method.getReturnType();
            if (method.getName().equals("stats")) {
                return new StatsSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, new ArrayList<>());
            }
            if (type == List.class) {
                return Collections.emptyList();
            }
            if (type == Map.class) {
                return Collections.emptyMap();
            }
            if (type == CompletionStage.class) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            if (type == String.class) {
                return "1.0";
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            return type == boolean.class ? (Object) false : null;
        });
    }

    @Test
    void listScreensOpenOnAnEmptyServer() {
        final TestMenuViewer viewer = new TestMenuViewer();
        final MenuRouter router = new MenuRouter(new MenuSession(emptyData(), new ImmediateHost()));
        Map<String, Runnable> screens = new LinkedHashMap<>();
        screens.put("plugins", () -> router.plugins(viewer, Filter.ALL, 0));
        screens.put("history", () -> router.history(viewer, null, 0));
        screens.put("backups", () -> router.backups(viewer, 0));
        screens.put("journal", () -> router.journal(viewer, null, 0));
        screens.put("tasks", () -> router.tasks(viewer, TaskPeriod.TODAY, TaskState.ALL, 0));
        screens.put("modules", () -> router.modules(viewer, null, 0, who -> { }));
        screens.put("libraries", () -> router.libraries(viewer, null, 0, who -> { }));
        screens.put("disk", () -> router.disk(viewer, 0, who -> { }));
        screens.put("groups", () -> router.groups(viewer, 0));
        screens.put("placeholders", () -> router.placeholders(viewer, 0));
        screens.put("actions", () -> router.actions(viewer, 0));
        screens.put("configs", () -> router.configs(viewer, 0));
        screens.put("missing", () -> router.missingPlugins(viewer, 0));
        screens.put("home", () -> router.home(viewer));
        screens.put("settings", () -> router.settings(viewer));
        screens.put("tools", () -> router.tools(viewer));
        screens.put("support", () -> router.support(viewer));
        screens.put("stats", () -> router.stats(viewer));
        screens.put("network", () -> router.network(viewer, 0));
        screens.put("bus", () -> router.busHome(viewer));
        screens.put("busDays", () -> router.busDays(viewer, null, 0));
        screens.put("busTraces", () -> router.busTraces(viewer, null, 0));
        screens.put("librarySettings", () -> router.librarySettings(viewer, 0));

        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, Runnable> screen : screens.entrySet()) {
            try {
                screen.getValue().run();
            } catch (Throwable error) {
                failed.add(screen.getKey());
            }
        }

        assertTrue(failed.isEmpty(), "screens failed on an empty server: " + failed);
        assertTrue(viewer.opened.size() >= screens.size() - 4);
    }
}
