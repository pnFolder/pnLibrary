package org.pnfolder.pnlibrary.core.updates.databases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.config.DatabaseKind
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class DatabaseBackupsTest {
    @TempDir
    lateinit var directory: Path

    private val restored = mutableListOf<Path>()
    private val engine = object : DatabaseEngine {
        override fun dump(connection: DatabaseConnection, tables: List<String>, target: Path) {
            Files.write(target, "dump".toByteArray())
        }

        override fun restore(connection: DatabaseConnection, source: Path) {
            restored.add(source)
        }
    }

    private fun backups(sources: DatabaseSources = DatabaseSources()) =
        DatabaseBackups(directory.resolve("databases"), sources, mapOf(DatabaseKind.MYSQL to engine))

    @Test
    fun `restore without a saved copy fails and touches nothing`() {
        val error = assertThrows(IllegalArgumentException::class.java) { backups().restore(UUID.randomUUID(), "pnclans", "Кланы") }
        assertTrue(error.message!!.contains("нет сохранённой базы"))
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `restore of a copy whose plugin no longer describes the database fails before restoring`() {
        val plan = UUID.randomUUID()
        val saved = directory.resolve("databases/$plan/pnclans")
        Files.createDirectories(saved)
        Files.write(saved.resolve("%D0%9A.db.gz"), ByteArray(3))
        assertThrows(IllegalStateException::class.java) { backups().restore(plan, "pnclans", "К") }
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `items of a plan without copies is empty`() {
        assertEquals(emptyList<Any>(), backups().items(UUID.randomUUID()))
    }

    @Test
    fun `copying nothing creates nothing`() {
        val plan = UUID.randomUUID()
        backups().copyBefore(plan, listOf("pnclans"))
        assertTrue(!Files.exists(directory.resolve("databases/$plan")))
    }
}
