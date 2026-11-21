package org.pnfolder.pnlibrary.core.menu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.core.menu.data.MenuData
import org.pnfolder.pnlibrary.core.menu.style.Row

class MenuSessionTest {
    private fun item(name: String) = ItemSpec(null, "STONE", null, name, emptyList())

    private fun anyMenu(): Menu = org.mockito.ArgumentMatchers.any(Menu::class.java) ?: Menu("", 1, emptyMap())

    private fun session() = MenuSession(mock(MenuData::class.java), mock(MenuHost::class.java))

    private fun shown(session: MenuSession, rows: List<Row>, page: Int, perPage: Int = 3, total: Int = rows.size): Menu {
        val viewer = mock(MenuViewer::class.java)
        var menu: Menu? = null
        org.mockito.Mockito.doAnswer { menu = it.getArgument(0); null }.`when`(viewer).open(anyMenu())
        session.list(viewer, "Список", rows, page, perPage, { _, _ -> }, { }, "пусто", total)
        return menu!!
    }

    @Test
    fun `builder refuses slots outside the chest and more than six rows`() {
        assertThrows(IllegalArgumentException::class.java) { Menus.chest("t").rows(7) }
        assertThrows(IllegalArgumentException::class.java) { Menus.chest("t").rows(1).slot(9, item("x")) }
    }

    @Test
    fun `fill never overwrites a slot that is already set`() {
        val menu = Menus.chest("t").rows(1).slot(0, item("own")).fill(item("pane")).build()

        assertEquals("own", menu.items.getValue(0).item!!.name)
        assertEquals("pane", menu.items.getValue(1).item!!.name)
        assertEquals(9, menu.items.size)
    }

    @Test
    fun `a middle page shows only its rows and both page buttons`() {
        val rows = (1..7).map { Row(item("r$it")) }

        val menu = shown(session(), rows, page = 1)

        assertEquals("r4", menu.items.getValue(0).item!!.name)
        assertEquals("r6", menu.items.getValue(2).item!!.name)
        assertTrue(menu.title.contains("2/3"))
        assertNotNull(menu.items.getValue(48).handler)
        assertNotNull(menu.items.getValue(50).handler)
    }

    @Test
    fun `an empty list says so and a page past the end is pulled back`() {
        val empty = shown(session(), emptyList(), page = 5)
        assertTrue(empty.items.getValue(22).item!!.name.replace(Regex("§."), "").contains("пусто"))
        assertTrue(empty.title.contains("1/1"))

        val last = shown(session(), (1..4).map { Row(item("r$it")) }, page = 99)
        assertTrue(last.title.contains("2/2"))
        assertNull(last.items.getValue(50).handler)
    }

    @Test
    fun `without the right the player gets a refusal and the action does not run`() {
        val viewer = mock(MenuViewer::class.java)
        var ran = false

        session().allowed(viewer, "pnlibrary.tasks") { ran = true }

        assertFalse(ran)
        verify(viewer).send(org.mockito.ArgumentMatchers.anyList())
    }

    @Test
    fun `an administrator passes every check`() {
        val viewer = mock(MenuViewer::class.java)
        `when`(viewer.hasPermission("pnlibrary.admin")).thenReturn(true)
        var ran = false

        session().allowed(viewer, "pnlibrary.tasks") { ran = true }

        assertTrue(ran)
    }
}
