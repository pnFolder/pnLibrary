package ru.privatenull.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.api.commands.CommandNodeKind
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.updates.BlockedReason
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.ProductChange
import ru.privatenull.pnlibrary.api.updates.UpdatePlan
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import java.lang.reflect.Proxy
import java.util.UUID

class BukkitControlCommandTest {
    @Test
    fun `control definition exposes admin command and actions through shared builder`() {
        val definition = BukkitControlCommand(
            plugin = proxy(Plugin::class.java),
            library = proxy(PnLibrary::class.java),
        ).definition()

        assertEquals("pn", definition.name)
        assertEquals("pnlibrary.admin", definition.permission)
        assertEquals(
            listOf("status", "updates", "check", "update", "update-confirm", "update-status", "update-rollback", "restart", "debug", "support", "error", "error-repeat", "error-chain"),
            definition.root.children.map { it.name },
        )
        val update = definition.root.children.single { it.name == "update" }
        assertEquals(CommandNodeKind.ARGUMENT, update.children.single().kind)
        assertEquals("plugin", update.children.single().name)
        val restart = definition.root.children.single { it.name == "restart" }
        assertEquals("token", restart.children.single().name)
    }

    @Test
    fun `update status messages explain current paused blocked failed and available states`() {
        val product = ProductId.of("acceptance")
        val blocked = BlockedReason.NoCompatibleRelease(product, 2)

        assertEquals(
            listOf("§aПлагин acceptance уже использует актуальную версию."),
            BukkitUpdateMessages.status(snapshot(UpdateState.UP_TO_DATE)),
        )
        assertEquals(
            listOf("§eОбновления плагина acceptance временно приостановлены."),
            BukkitUpdateMessages.status(snapshot(UpdateState.FROZEN)),
        )
        assertEquals(
            listOf(
                "§cОбновление плагина acceptance заблокировано.",
                "§7Причина: для плагина acceptance нет версии, совместимой с pnLibrary API 2.",
            ),
            BukkitUpdateMessages.status(snapshot(UpdateState.BLOCKED, blockers = listOf(blocked))),
        )
        assertEquals(
            listOf("§cНе удалось проверить обновление плагина acceptance: GitHub вернул HTTP 404."),
            BukkitUpdateMessages.status(snapshot(UpdateState.FAILED, message = "GitHub вернул HTTP 404")),
        )
        assertEquals(
            listOf("§eДля плагина acceptance доступно обновление: 2.2.0 → 2.5.0."),
            BukkitUpdateMessages.status(snapshot(UpdateState.UPDATE_AVAILABLE, latest = "2.5.0", changed = true)),
        )
        assertEquals(
            listOf("§aПлагин acceptance уже использует актуальную версию."),
            BukkitUpdateMessages.status(snapshot(UpdateState.UPDATE_STAGED, latest = "2.5.0")),
        )
        assertEquals(
            listOf("§aОбновление плагина acceptance проверено и подготовлено. Полностью перезапустите сервер."),
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
        ru.privatenull.pnlibrary.api.updates.UpdateSnapshot(
            "acceptance", "2.2.0", latest, ru.privatenull.pnlibrary.api.updates.UpdateChannel.STABLE,
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
