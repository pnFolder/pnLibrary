package ru.privatenull.pnlibrary.bukkit.commands

import ru.privatenull.pnlibrary.api.updates.BlockedReason
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState

/** Pure, platform-independent wording used by Bukkit update commands. */
internal object BukkitUpdateMessages {
    fun status(input: Pair<UpdatePlanSnapshot, UpdateSnapshot>): List<String> {
        val (plan, plugin) = input
        val isLibrary = plugin.product.equals("pnlibrary", true)
        val subject = if (isLibrary) {
            "библиотека pnLibrary"
        } else {
            "плагин ${plugin.product}"
        }
        val subjectGenitive = if (isLibrary) "библиотеки pnLibrary" else "плагина ${plugin.product}"
        val change = plan.plan?.changes?.firstOrNull { it.product.value.equals(plugin.product, true) }
        val state = when {
            plugin.state == UpdateState.FROZEN -> UpdateState.FROZEN
            plan.state == UpdateState.BLOCKED -> UpdateState.BLOCKED
            plan.state == UpdateState.FAILED -> UpdateState.FAILED
            plan.state == UpdateState.ROLLED_BACK -> UpdateState.ROLLED_BACK
            change != null && plan.state == UpdateState.UPDATE_STAGED -> UpdateState.UPDATE_STAGED
            change != null && (plugin.state in setOf(UpdateState.AVAILABLE, UpdateState.UPDATE_AVAILABLE) ||
                plan.state == UpdateState.UPDATE_AVAILABLE) -> UpdateState.UPDATE_AVAILABLE
            else -> UpdateState.UP_TO_DATE
        }
        return when (state) {
            UpdateState.FROZEN -> listOf("§eОбновления $subjectGenitive временно приостановлены.")
            UpdateState.BLOCKED -> buildList {
                add("§cОбновление $subjectGenitive заблокировано.")
                plan.blockers.forEach { add("§7Причина: ${blocker(it)}") }
            }
            UpdateState.FAILED -> listOf(
                "§cНе удалось проверить обновление $subjectGenitive: ${sentence(plan.message ?: plugin.message ?: "неизвестная ошибка")}",
            )
            UpdateState.UPDATE_AVAILABLE -> listOf(
                "§eДля $subjectGenitive доступно обновление: ${plugin.currentVersion} → ${plugin.latestVersion ?: change?.to ?: "неизвестно"}.",
            )
            UpdateState.UPDATE_STAGED -> listOf(
                "§aОбновление $subjectGenitive проверено и подготовлено. Полностью перезапустите сервер.",
            )
            UpdateState.ROLLED_BACK -> listOf(
                "§aПредыдущая версия $subjectGenitive подготовлена. Полностью перезапустите сервер.",
            )
            else -> listOf("§a${subject.replaceFirstChar { it.uppercase() }} уже использует актуальную версию.")
        }
    }

    private fun blocker(reason: BlockedReason): String = when (reason) {
        is BlockedReason.ApiMismatch ->
                "плагин ${reason.product} поддерживает pnLibrary API " +
                    "${reason.supportedApi.minimum}–${reason.supportedApi.maximum}, " +
                    "требуется API ${reason.requiredApi}."
        is BlockedReason.MissingDependency ->
            "для плагина ${reason.product} требуется ${reason.dependency} версии ${reason.minimumVersion} или новее."
        is BlockedReason.MissingExternalPluginDependency ->
            "для плагина ${reason.product} требуется внешний плагин ${reason.plugin} версии ${reason.minimumVersion} или новее."
        is BlockedReason.Frozen -> "обновления плагина ${reason.product} временно приостановлены."
        is BlockedReason.NoCompatibleRelease ->
            "для плагина ${reason.product} нет версии, совместимой с pnLibrary API ${reason.requiredApi}."
    }

    private fun sentence(value: String): String = value.trim().removeSuffix(".") + "."
}
