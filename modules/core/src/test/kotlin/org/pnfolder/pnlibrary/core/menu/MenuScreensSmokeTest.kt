package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor
import org.pnfolder.pnlibrary.core.commands.tasks.TaskPeriod
import org.pnfolder.pnlibrary.core.commands.tasks.TaskState
import org.pnfolder.pnlibrary.core.menu.data.MenuData
import org.pnfolder.pnlibrary.core.menu.screens.Filter
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Opens every list screen over a server that has nothing, so a screen that cannot show emptiness fails here. */
class MenuScreensSmokeTest {
    private class Viewer : MenuViewer {
        val opened = mutableListOf<Menu>()
        override val id: UUID = UUID.randomUUID()
        override val name = "Admin"
        override val online = true
        override val remote = false
        override val actor = UpdateActor(UpdateActor.Kind.PLAYER, id.toString(), name)
        override fun hasPermission(permission: String) = true
        override fun open(menu: Menu) { opened += menu }
        override fun close() {}
        override fun send(lines: List<Component>) {}
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

    private fun emptyData(): MenuData =
        Proxy.newProxyInstance(MenuData::class.java.classLoader, arrayOf(MenuData::class.java)) { _, method, _ ->
            when {
                method.name == "stats" -> StatsSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, emptyList())
                method.returnType == List::class.java -> emptyList<Any>()
                method.returnType == Map::class.java -> emptyMap<Any, Any>()
                method.returnType == CompletionStage::class.java -> CompletableFuture.completedFuture(emptyList<Any>())
                method.returnType == String::class.java -> "1.0"
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Long::class.javaPrimitiveType -> 0L
                method.returnType == Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        } as MenuData

    @Test
    fun `list screens open on an empty server`() {
        val viewer = Viewer()
        val router = MenuRouter(MenuSession(emptyData(), Host))
        val screens = mapOf<String, () -> Unit>(
            "plugins" to { router.plugins(viewer, Filter.ALL, 0) },
            "history" to { router.history(viewer, null, 0) },
            "backups" to { router.backups(viewer, 0) },
            "journal" to { router.journal(viewer, null, 0) },
            "tasks" to { router.tasks(viewer, TaskPeriod.TODAY, TaskState.ALL, 0) },
            "modules" to { router.modules(viewer, null, 0) { } },
            "libraries" to { router.libraries(viewer, null, 0) { } },
            "disk" to { router.disk(viewer, 0) { } },
            "groups" to { router.groups(viewer, 0) },
            "placeholders" to { router.placeholders(viewer, 0) },
            "actions" to { router.actions(viewer, 0) },
            "configs" to { router.configs(viewer, 0) },
            "missing" to { router.missingPlugins(viewer, 0) },
            "home" to { router.home(viewer) },
            "settings" to { router.settings(viewer) },
            "tools" to { router.tools(viewer) },
            "support" to { router.support(viewer) },
            "stats" to { router.stats(viewer) },
            "network" to { router.network(viewer, 0) },
            "bus" to { router.busHome(viewer) },
            "busDays" to { router.busDays(viewer, null, 0) },
            "busTraces" to { router.busTraces(viewer, null, 0) },
            "librarySettings" to { router.librarySettings(viewer, 0) },
        )
        val failed = screens.filter { (_, open) -> runCatching(open).isFailure }.keys

        assertTrue(failed.isEmpty(), "screens failed on an empty server: $failed")
        assertTrue(viewer.opened.size >= screens.size - 4)
    }
}
