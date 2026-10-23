package org.pnfolder.pnlibrary.bukkit.commands

import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.BlockedReason
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState

/** Pure, platform-independent wording of the update state used by Bukkit update commands. */
internal object BukkitUpdateMessages {
    /** Describes the effective plan and product state as short messages. */
    fun status(input: Pair<UpdatePlanSnapshot, UpdateSnapshot>): List<ChatNotice> {
        val (plan, plugin) = input
        val isLibrary = plugin.product.equals(PnLibraryIdentity.PRODUCT_ID, true)
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
            change != null && (plugin.state == UpdateState.UPDATE_AVAILABLE ||
                plan.state == UpdateState.UPDATE_AVAILABLE) -> UpdateState.UPDATE_AVAILABLE
            else -> UpdateState.UP_TO_DATE
        }
        return when (state) {
            UpdateState.FROZEN -> listOf(ChatNotice(ChatTone.WARNING, "Обновления $subjectGenitive временно приостановлены."))
            UpdateState.BLOCKED -> listOf(ChatNotice(ChatTone.FAILURE, "Обновление $subjectGenitive заблокировано.")) +
                plan.blockers.map { ChatNotice(ChatTone.WARNING, "Причина: ${blocker(it)}") }
            UpdateState.FAILED -> listOf(ChatNotice(ChatTone.FAILURE, "Не удалось проверить обновление $subjectGenitive.",
                sentence(plan.message ?: plugin.message ?: "неизвестная ошибка")))
            UpdateState.UPDATE_AVAILABLE -> listOf(ChatNotice(ChatTone.INFO,
                "Для $subjectGenitive доступно обновление: v${plugin.currentVersion} » v${plugin.latestVersion ?: change?.to ?: "?"}."))
            UpdateState.UPDATE_STAGED -> listOf(ChatNotice(ChatTone.SUCCESS, "Обновление $subjectGenitive проверено и подготовлено.",
                "Полностью перезапустите сервер."))
            UpdateState.ROLLED_BACK -> listOf(ChatNotice(ChatTone.SUCCESS, "Предыдущая версия $subjectGenitive подготовлена.",
                "Полностью перезапустите сервер."))
            else -> listOf(ChatNotice(ChatTone.SUCCESS, "${subject.replaceFirstChar { it.uppercase() }} уже использует актуальную версию."))
        }
    }

    /** Explains the compatibility or policy reason blocking an update. */
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

    /** Ensures diagnostic messages end with exactly one period. */
    private fun sentence(value: String): String = value.trim().removeSuffix(".") + "."
}
