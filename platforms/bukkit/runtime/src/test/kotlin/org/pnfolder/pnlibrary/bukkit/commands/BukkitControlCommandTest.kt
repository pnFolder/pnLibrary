package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.CommandNodeKind
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.BlockedReason
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.release.ProductChange
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan
import org.pnfolder.pnlibrary.api.version.PnLibraryApi
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.lang.reflect.Proxy
import java.util.UUID

class BukkitControlCommandTest {
    @Test
    fun `control definition exposes admin command and actions through shared builder`() {
        val definition = BukkitControlCommand(
            plugin = proxy(Plugin::class.java),
            library = proxy(PnLibrary::class.java),
            currencies = proxy(CurrencyProviderRegistry::class.java),
        ).definition()

        assertEquals("pn", definition.name)
        // The root has no permission of its own: every administrative word asks for pnlibrary.admin by itself.
        assertEquals(null, definition.permission)
        assertEquals(
            listOf("status", "check", "gui", "updates", "update", "support", "bus", "tasks", "money"),
            definition.root.children.map { it.name },
        )
        val administrative = definition.root.children.filter { it.name != "money" && it.name != "bus" && it.name != "tasks" }
        assertTrue(administrative.all { it.permission == "pnlibrary.admin" }, administrative.map { it.name }.toString())
        val update = definition.root.children.single { it.name == "update" }
        // A narrower permission opens the same word next to the wide one.
        assertEquals(listOf("pnlibrary.updates.download"), update.alsoPermissions)
        val catalog = definition.root.children.single { it.name == "updates" }
        assertEquals(listOf("list", "available", "info", "settings", "set", "history", "backups", "rollback"), catalog.children.map { it.name })
        assertEquals(listOf("page", "plugin"), catalog.children.single { it.name == "history" }.children.map { it.name })
        assertEquals("plugin", catalog.children.single { it.name == "info" }.children.single().name)
        assertEquals(listOf("plugin", "confirm", "rollback", "restart"), update.children.map { it.name })
        assertEquals(CommandNodeKind.ARGUMENT, update.children.first().kind)
        assertEquals("token", update.children.single { it.name == "confirm" }.children.single().name)
    }

    @Test
    fun `update status messages explain current paused blocked failed and available states`() {
        val product = ProductId.of("acceptance")
        val blocked = BlockedReason.NoCompatibleRelease(product, 2)

        assertEquals(
            listOf(ChatNotice(ChatTone.SUCCESS, "Плагин acceptance уже использует актуальную версию.")),
            BukkitUpdateMessages.status(snapshot(UpdateState.UP_TO_DATE)),
        )
        assertEquals(
            listOf(ChatNotice(ChatTone.WARNING, "Обновления плагина acceptance временно приостановлены.")),
            BukkitUpdateMessages.status(snapshot(UpdateState.FROZEN)),
        )
        assertEquals(
            listOf(
                ChatNotice(ChatTone.FAILURE, "Обновление плагина acceptance заблокировано."),
                ChatNotice(ChatTone.WARNING, "Причина: для плагина acceptance нет версии, совместимой с pnLibrary API 2."),
            ),
            BukkitUpdateMessages.status(snapshot(UpdateState.BLOCKED, blockers = listOf(blocked))),
        )
        assertEquals(
            listOf(ChatNotice(ChatTone.FAILURE, "Не удалось проверить обновление плагина acceptance.", "GitHub вернул HTTP 404.")),
            BukkitUpdateMessages.status(snapshot(UpdateState.FAILED, message = "GitHub вернул HTTP 404")),
        )
        assertEquals(
            listOf(ChatNotice(ChatTone.INFO, "Для плагина acceptance доступно обновление: v2.2.0 » v2.5.0.")),
            BukkitUpdateMessages.status(snapshot(UpdateState.UPDATE_AVAILABLE, latest = "2.5.0", changed = true)),
        )
        assertEquals(
            listOf(ChatNotice(ChatTone.SUCCESS, "Плагин acceptance уже использует актуальную версию.")),
            BukkitUpdateMessages.status(snapshot(UpdateState.UPDATE_STAGED, latest = "2.5.0")),
        )
        assertEquals(
            listOf(ChatNotice(ChatTone.SUCCESS, "Обновление плагина acceptance проверено и подготовлено.", "Полностью перезапустите сервер.")),
            BukkitUpdateMessages.status(snapshot(UpdateState.UPDATE_STAGED, latest = "2.5.0", changed = true)),
        )
    }

    private fun snapshot(
        state: UpdateState,
        latest: String? = null,
        blockers: List<BlockedReason> = emptyList(),
        message: String? = null,
        changed: Boolean = false,
    ) = UpdatePlanSnapshot(UUID.randomUUID(), 1, state, if (changed) UpdatePlan(
        PnLibraryApi.VERSION,
        listOf(ProductChange(ProductId.of("acceptance"), SemanticVersion.parse("2.2.0"), SemanticVersion.parse(latest ?: "2.5.0"))),
        emptyList(),
    ) else null, blockers, message) to
        UpdateSnapshot(
            "acceptance", "2.2.0", latest, UpdateChannel.STABLE,
            state, 17, 8, false, null, message,
        )

    private class TestSender : CommandSender {
        override val id = "console"
        override val name = "Console"
        override val isConsole = true
        override fun hasPermission(permission: String) = true
        override fun send(message: Component) = Unit
    }

    private fun <T> proxy(type: Class<T>): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, _ ->
            when (method.name) {
                "toString" -> type.simpleName
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> false
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Void.TYPE -> Unit
                    else -> null
                }
            }
        },
    )
}
