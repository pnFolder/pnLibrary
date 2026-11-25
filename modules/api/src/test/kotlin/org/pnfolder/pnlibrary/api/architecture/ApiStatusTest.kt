package org.pnfolder.pnlibrary.api.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Interfaces that only the library implements say so, as Paper does: a plugin author sees at once what not to implement. */
class ApiStatusTest {
    private val api: Path = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }
        .resolve("modules/api/src/main/kotlin/org/pnfolder/pnlibrary/api")

    private val libraryOnly = listOf("runtime/PnLibrary.kt","plugin/ModuleContext.kt","plugin/PluginRegistration.kt","plugin/PluginRegistry.kt","services/ServiceManager.kt","expansions/ExpansionManager.kt","events/bus/EventService.kt","events/bus/EventScope.kt","tasks/TaskScope.kt","tasks/TaskHandle.kt","placeholders/PlaceholderService.kt","scenario/actions/ActionService.kt","cooldowns/CooldownService.kt","cooldowns/Cooldown.kt","metrics/MetricsService.kt","logging/LoggingService.kt","observability/ObservabilityService.kt","diagnostics/DiagnosticsService.kt","downloads/DownloadRegistration.kt","libraries/ModuleLibraries.kt")

    @Test
    fun `interfaces that only the library implements say so`() {
        val missing = (listOf("runtime/group/Monitoring.kt", "runtime/group/Registrations.kt", "runtime/group/RuntimeInfo.kt") + libraryOnly)
            .filter { "@ApiStatus.NonExtendable" !in Files.readString(api.resolve(it)) }
        assertTrue(missing.isEmpty(), "Missing @ApiStatus.NonExtendable: $missing")
    }
}
