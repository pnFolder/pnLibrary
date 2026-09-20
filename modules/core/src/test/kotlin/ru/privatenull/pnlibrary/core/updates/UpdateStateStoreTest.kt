package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class UpdateStateStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `persists current state atomically and bounds newest first history`() {
        val store = UpdateStateStore(directory, maximumEntries = 3, maximumBytes = 1024 * 1024)
        val snapshots = (1L..5L).map { revision ->
            UpdatePlanSnapshot(UUID.randomUUID(), revision, UpdateState.CURRENT, null, emptyList(), "revision-$revision")
                .also(store::save)
        }

        assertEquals(5, store.current()?.revision)
        assertEquals(listOf(5L, 4L, 3L), store.history().map(UpdatePlanSnapshot::revision))
        assertFalse(Files.exists(directory.resolve("state.json.tmp")))
        assertEquals(3, Files.list(directory.resolve("history")).use { it.count() })
        assertEquals(snapshots.last().id, store.current()?.id)
    }

    @Test
    fun `corrupt state is quarantined instead of breaking startup`() {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("state.json"), "not-json")
        val warnings = mutableListOf<String>()

        val store = UpdateStateStore(directory, warning = warnings::add)

        assertNull(store.current())
        assertEquals(1, warnings.size)
        assertTrue(Files.list(directory).use { files -> files.anyMatch { it.fileName.toString().startsWith("state.json.corrupt-") } })
    }

    @Test
    fun `restores the complete selected plan after restart`() {
        val component = ComponentId.of("pnlibrary")
        val version = SemanticVersion.parse("2.0.0")
        val plan = UpdatePlan(1, listOf(ComponentChange(component, SemanticVersion.parse("1.0.0"), version)),
            listOf(ComponentRelease(component, version, UpdateChannel.STABLE, ApiVersionRange(1, 1), providesApi = 1)))
        val snapshot = UpdatePlanSnapshot(UUID.randomUUID(), 9, UpdateState.UPDATE_AVAILABLE, plan, emptyList(), null)

        UpdateStateStore(directory).save(snapshot)
        val restored = UpdateStateStore(directory).current()

        assertEquals("pnlibrary", restored?.plan?.changes?.single()?.component?.value)
        assertEquals("2.0.0", restored?.plan?.selected?.single()?.version.toString())
    }
}
