package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.runtime.lifecycle.RuntimeShutdownPlan;

class RuntimeShutdownPlanTest {
    private static AutoCloseable step(List<String> calls, String name) {
        return () -> calls.add(name);
    }

    @Test
    void pluginExpansionsCloseBeforeTheLibrarysOwnWhichCloseBeforeServices() {
        List<String> calls = new ArrayList<>();

        new RuntimeShutdownPlan(step(calls, "plugins"), step(calls, "commands"), step(calls, "players"), step(calls, "platforms"),
            step(calls, "currency"), step(calls, "expansions"), step(calls, "libraryExpansions"), step(calls, "configurations"),
            () -> null, step(calls, "metrics"), step(calls, "updates"), step(calls, "events"), step(calls, "services"),
            step(calls, "tasks"), step(calls, "support"), () -> calls.add("releaseProvider"), () -> calls.add("closePlatform"),
            () -> calls.add("onClose")).run();

        assertEquals(Arrays.asList(
            "plugins", "commands", "players", "platforms", "currency", "expansions", "libraryExpansions", "configurations",
            "metrics", "updates", "events", "services", "tasks", "support", "releaseProvider", "closePlatform", "onClose"), calls);
    }
}
