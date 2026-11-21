package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor
import org.pnfolder.pnlibrary.core.menu.data.MenuData
import org.pnfolder.pnlibrary.core.menu.screens.ConfigRow
import org.pnfolder.pnlibrary.core.menu.style.BACK
import org.pnfolder.pnlibrary.core.menu.style.CONFIGS
import org.pnfolder.pnlibrary.core.menu.style.FILTER
import org.pnfolder.pnlibrary.core.menu.style.HOME_CLOSE
import org.pnfolder.pnlibrary.core.menu.style.NEXT
import org.pnfolder.pnlibrary.core.menu.style.PAGE_SIZE
import org.pnfolder.pnlibrary.core.menu.style.PLACEHOLDERS
import org.pnfolder.pnlibrary.core.menu.style.PREVIOUS
import org.pnfolder.pnlibrary.core.placeholders.entries.PlaceholderInfo
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
    private fun data(placeholders: List<PlaceholderInfo> = emptyList(), configs: List<ConfigRow> = emptyList()): MenuData =
        Proxy.newProxyInstance(MenuData::class.java.classLoader, arrayOf(MenuData::class.java)) { _, method, _ ->
            when {
                method.name == "placeholders" -> placeholders
                method.name == "configs" -> configs
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

    private fun file(id: String, editable: Int = 1) = ConfigRow(id, "Model", true, false, editable, null)

    private fun title(menu: Menu) = menu.title.replace(Regex("§."), "")

    @Test
    fun `configurations go from plugins to modules to files and back`() {
        val viewer = Viewer(setOf(CONFIGS))
        val files = listOf(file("shop/main:a.yml"), file("shop/main:b.yml"), file("shop/vip:c.yml"), file("clans/core:d.yml"))
        LibraryMenu(data(configs = files), Host).home(viewer)
        click(viewer, viewer.opened.last(), 21)

        val plugins = viewer.opened.last()
        assertTrue(title(plugins).contains("Конфигурации"))
        assertEquals(2, plugins.items.count { (slot, item) -> slot < PAGE_SIZE && item.item != null && item.handler != null })

        assertNotNull(plugins.items[FILTER]?.handler)
        click(viewer, plugins, FILTER) // every file of every plugin, with plugin and module named
        assertEquals(4, viewer.opened.last().items.count { (slot, item) -> slot < PAGE_SIZE && item.item != null && item.handler != null })
        click(viewer, viewer.opened.last(), FILTER) // back to the plugins

        click(viewer, plugins, 0) // clans: one module, so its files are shown at once
        assertTrue(title(viewer.opened.last()).contains("clans"))
        assertNotNull(viewer.opened.last().items[FILTER]?.handler) // the choice between modules and files is always there

        click(viewer, plugins, 1) // shop: two modules
        val shop = viewer.opened.last()
        assertEquals(2, shop.items.count { (slot, item) -> slot < PAGE_SIZE && item.item != null && item.handler != null })
        assertNotNull(shop.items[FILTER]?.handler)

        click(viewer, shop, 0) // module main
        val main = viewer.opened.last()
        assertTrue(title(main).contains("shop · main"))
        assertEquals(2, main.items.count { (slot, item) -> slot < PAGE_SIZE && item.item != null && item.handler != null })

        click(viewer, main, BACK)
        assertTrue(title(viewer.opened.last()).contains("shop"))

        click(viewer, shop, FILTER) // every file of the plugin, with its module named
        val all = viewer.opened.last()
        assertEquals(3, all.items.count { (slot, item) -> slot < PAGE_SIZE && item.item != null && item.handler != null })
    }

    @Test
    fun `a plugin with many files is cut into pages`() {
        val viewer = Viewer(setOf(CONFIGS))
        val files = (1..50).map { file("big/core:f$it.yml") }
        LibraryMenu(data(configs = files), Host).home(viewer)
        click(viewer, viewer.opened.last(), 21)
        click(viewer, viewer.opened.last(), 0)

        val first = viewer.opened.last()
        assertTrue(first.title.contains("1/2"))
        click(viewer, first, NEXT)
        assertTrue(viewer.opened.last().title.contains("2/2"))
    }
}
