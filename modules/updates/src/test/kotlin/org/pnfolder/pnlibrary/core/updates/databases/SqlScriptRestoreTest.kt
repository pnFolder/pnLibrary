package org.pnfolder.pnlibrary.core.updates.databases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.util.zip.GZIPOutputStream

class SqlScriptRestoreTest {
    @TempDir
    lateinit var directory: Path

    private val statement = mock(Statement::class.java)
    private val connection = mock(Connection::class.java).also { `when`(it.createStatement()).thenReturn(statement) }

    private fun script(vararg lines: String): Path {
        val file = directory.resolve("copy.gz")
        GZIPOutputStream(Files.newOutputStream(file)).use { it.write(lines.joinToString("\n").toByteArray()) }
        return file
    }

    @Test
    fun `an atomic restore commits after the last statement`() {
        SqlScriptRestore("-- h", atomic = true).read(connection, script("-- h", "A", "B"))
        val order = inOrder(connection, statement)
        order.verify(connection).autoCommit = false
        order.verify(statement).execute("A")
        order.verify(statement).execute("B")
        order.verify(connection).commit()
    }

    @Test
    fun `an atomic restore rolls back when a statement fails`() {
        `when`(statement.execute("B")).thenThrow(SQLException("boom"))
        assertThrows(SQLException::class.java) { SqlScriptRestore("-- h", atomic = true).read(connection, script("-- h", "A", "B")) }
        verify(connection).rollback()
        verify(connection, never()).commit()
    }

    @Test
    fun `a plain restore uses autocommit and a foreign file is refused`() {
        SqlScriptRestore("-- h").read(connection, script("-- h", "A"))
        verify(connection).autoCommit = true
        verify(connection, never()).commit()
        assertThrows(IllegalArgumentException::class.java) { SqlScriptRestore("-- h").read(connection, script("-- other", "A")) }
        assertEquals(1, Files.list(directory).use { it.count() })
    }
}
