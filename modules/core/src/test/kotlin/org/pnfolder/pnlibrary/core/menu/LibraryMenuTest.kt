package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderInfo
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class LibraryMenuTest {
    private class Viewer(private val held: Set<String>) : MenuViewer {
        val opened = mutableListOf<Menu>()
        val sent = mutableListOf<Component>()
        override val id: UUID = UUID.randomUUID()
        override val name = "Admin"
        override val online = true
        override val remote = false
        override val actor = UpdateActor(UpdateActor.Kind.PLAYER, id.toString(), name)
        override fun hasPermission(permission: String) = permission in held
        override fun open(menu: Menu) { opened += menu }
        override fun close() {}
        override fun send(lines: List<Component>) { sent += lines }
        override fun runCommand(command: String) {}
    }

    private object Host : MenuHost {
        override fun sync(action: () -> Unit) = action()
        override fun later(delayMillis: Long, action: () -> Unit) = action()
        override fun async(action: () -> Unit) = action()
        override fun openRemote(viewer: MenuViewer, target: String) = false
        override fun owner(owner: Any?): OwnerInfo? = null
        override fun ownerNamed(name: String): OwnerInfo? = null
        override fun ownerOf(type: Class<*>?): OwnerInfo? = null
    }

    /** Facts that are all empty, except what a test gives: lists are empty, texts are plain, the rest is nothing. */
    private fun data(placeholders: List<PlaceholderInfo> = emptyList()): MenuData =
        Proxy.newProxyInstance(MenuData::class.java.classLoader, arrayOf(MenuData::class.java)) { _, method, _ ->
            when {
                method.name == "placeholders" -> placeholders
                method.returnType == List::class.java -> emptyList<Any>()
                method.returnType == CompletionStage::class.java -> CompletableFuture.completedFuture(emptyList<Any>())
                method.returnType == String::class.java -> "1.0"
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        } as MenuData

    private fun click(viewer: Viewer, menu: Menu, slot: Int) {
        menu.items.getValue(slot).handler!!.handle(MenuClick(viewer, slot, ClickType.LEFT))
    }

    private fun placeholders(count: Int) = (1..count).map { PlaceholderInfo("pnclans/war:p$it", "Integer", null, emptyList(), true) }

    @Test
    fun `the home page opens with a close button and the way to every section`() {
        val viewer = Viewer(setOf("pnlibrary.admin"))

        LibraryMenu(data(), Host).home(viewer)

        val home = viewer.opened.single()
        assertTrue(home.items.containsKey(HOME_CLOSE))
        assertNotNull(home.items[23]?.handler)
        assertNotNull(home.items[25]?.handler)
    }

    @Test
    fun `a section opens for a viewer with its permission and is refused without it`() {
        val allowed = Viewer(setOf(PLACEHOLDERS))
        LibraryMenu(data(placeholders(2)), Host).home(allowed)
        click(allowed, allowed.opened.last(), 23)
        assertTrue(allowed.opened.last().title.contains("Плейсхолдеры"))

        val refused = Viewer(emptySet())
        LibraryMenu(data(), Host).home(refused)
        click(refused, refused.opened.last(), 23)
        assertEquals(1, refused.opened.size)
        assertTrue(refused.sent.isNotEmpty())
    }

    @Test
    fun `a long list is cut into pages that lead to each other`() {
        val viewer = Viewer(setOf(PLACEHOLDERS))
        LibraryMenu(data(placeholders(50)), Host).home(viewer)
        click(viewer, viewer.opened.last(), 23)

        val first = viewer.opened.last()
        assertTrue(first.title.contains("1/2"))
        assertNotNull(first.items[NEXT]?.handler)
        assertNull(first.items[PREVIOUS]?.handler)

        click(viewer, first, NEXT)
        val second = viewer.opened.last()
        assertTrue(second.title.contains("2/2"))
        assertNotNull(second.items[PREVIOUS]?.handler)
        assertNull(second.items[NEXT]?.handler)
    }

    @Test
    fun `an empty list says so instead of showing nothing`() {
        val viewer = Viewer(setOf(PLACEHOLDERS))
        LibraryMenu(data(), Host).home(viewer)
        click(viewer, viewer.opened.last(), 23)

        assertEquals("Плейсхолдеров нет", viewer.opened.last().items.getValue(22).item!!.name.replace(Regex("§."), ""))
    }
}
