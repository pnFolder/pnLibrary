package org.pnfolder.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.core.runtime.lifecycle.RuntimeShutdownPlan

class RuntimeShutdownPlanTest {
    @Test
    fun `plugin expansions close before the library's own, which close before services`() {
        val calls = mutableListOf<String>()
        fun step(name: String) = AutoCloseable { calls += name }

        RuntimeShutdownPlan(
            plugins = step("plugins"),
            commands = step("commands"),
            audiences = step("audiences"),
            platforms = step("platforms"),
            currency = step("currency"),
            expansions = step("expansions"),
            libraryExpansions = step("libraryExpansions"),
            configurations = step("configurations"),
            libraryMetrics = { null },
            metrics = step("metrics"),
            updates = step("updates"),
            events = step("events"),
            services = step("services"),
            tasks = step("tasks"),
            support = step("support"),
            releaseProvider = { calls += "releaseProvider" },
            closePlatform = { calls += "closePlatform" },
            onClose = { calls += "onClose" },
        ).run()

        assertEquals(
            listOf(
                "plugins", "commands", "audiences", "platforms", "currency", "expansions", "libraryExpansions", "configurations",
                "metrics", "updates", "events", "services", "tasks", "support", "releaseProvider", "closePlatform", "onClose",
            ),
            calls,
        )
    }
}
