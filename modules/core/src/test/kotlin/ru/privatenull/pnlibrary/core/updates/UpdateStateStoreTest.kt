package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
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
}
