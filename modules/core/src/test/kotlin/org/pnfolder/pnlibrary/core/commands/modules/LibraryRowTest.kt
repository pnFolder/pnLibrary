package org.pnfolder.pnlibrary.core.commands.modules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryRowTest {
    private fun row(owner: String) = LibraryRow(owner, "Hikari", "g:a:1", "1", null, emptyList(), true, true, false, false)

    @Test
    fun `a plugin owns its own libraries and those of its modules but not another plugin's`() {
        assertTrue(row("shop").belongsTo("shop"))
        assertTrue(row("shop:cart").belongsTo("shop"))
        assertTrue(row("shop:cart").belongsTo("shop:cart"))
        assertFalse(row("shop:cart").belongsTo("shop:other"))
        assertFalse(row("shopping").belongsTo("shop"))
    }

    @Test
    fun `whose library it is is told in words and split into plugin and module`() {
        assertEquals("плагину shop", row("shop").ownerText)
        assertEquals("модулю cart плагина shop", row("shop:cart").ownerText)
        assertEquals("shop", row("shop:cart").plugin)
        assertEquals("cart", row("shop:cart").module)
        assertNull(row("shop").module)
    }

    @Test
    fun `the place is counted from the server folder and split into folder and file`() {
        val root = java.nio.file.Paths.get("srv")
        val place = LibraryRow.placeOf(root.resolve("plugins/pnLibrary/libs/a/b-1.jar").toString(), root)
        assertEquals("plugins/pnLibrary/libs/a/b-1.jar", place)
        val row = row("shop").copy(place = place)
        assertEquals("plugins/pnLibrary/libs/a", row.folder)
        assertEquals("b-1.jar", row.fileName)
        assertNull(row("shop").folder)
    }
}
