package org.pnfolder.pnlibrary.api.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Interfaces that only the library implements say so, as Paper does: a plugin author sees at once what not to implement. */
class ApiStatusTest {
    private static final Path API =
        ProjectRoot.find().resolve("modules/api/src/main/java/org/pnfolder/pnlibrary/api");

    private static final List<String> LIBRARY_ONLY = Arrays.asList(
        "runtime/group/Monitoring.java", "runtime/group/Registrations.java", "runtime/group/RuntimeInfo.java",
        "runtime/PnLibrary.java", "plugin/ModuleContext.java", "plugin/PluginRegistration.java",
        "plugin/PluginRegistry.java", "services/ServiceManager.java", "expansions/ExpansionManager.java",
        "events/bus/EventService.java", "events/bus/EventScope.java", "tasks/TaskScope.java", "tasks/TaskHandle.java",
        "placeholders/PlaceholderService.java", "scenario/actions/ActionService.java",
        "cooldowns/CooldownService.java", "cooldowns/Cooldown.java", "metrics/MetricsService.java",
        "logging/LoggingService.java", "observability/ObservabilityService.java",
        "diagnostics/DiagnosticsService.java", "downloads/DownloadRegistration.java", "libraries/ModuleLibraries.java");

    @Test
    @DisplayName("interfaces that only the library implements say so")
    void interfacesThatOnlyTheLibraryImplementsSaySo() throws IOException {
        assertTrue(Files.isDirectory(API), "API sources not found at " + API);
        List<String> missing = new ArrayList<>();
        for (String path : LIBRARY_ONLY) {
            Path file = API.resolve(path);
            assertTrue(Files.isRegularFile(file), "Listed API source does not exist: " + file);
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            if (!text.contains("@ApiStatus.NonExtendable")) {
                missing.add(path);
            }
        }
        assertTrue(missing.isEmpty(), "Missing @ApiStatus.NonExtendable: " + missing);
    }
}
