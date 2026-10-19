package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.core.ui.ChatPalette
import org.pnfolder.pnlibrary.core.updates.install.ComponentLedger

/** The words and colors of update states and history operations, shared by every catalog page. */
internal object CatalogLabels {
    /** States with an update on its way: available, downloading or waiting for a restart. */
    val PENDING: Set<UpdateState> = setOf(UpdateState.UPDATE_AVAILABLE, UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING)

    /** States that need the administrator's attention. */
    val PROBLEMS: Set<UpdateState> = setOf(UpdateState.FAILED, UpdateState.BLOCKED, UpdateState.INCOMPATIBLE)

    fun stateColor(state: UpdateState): TextColor = when (state) {
        UpdateState.UP_TO_DATE -> ChatPalette.OK
        UpdateState.UPDATE_AVAILABLE -> ChatPalette.INFO
        UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING, UpdateState.ROLLED_BACK -> ChatPalette.ORANGE
        UpdateState.FAILED, UpdateState.BLOCKED, UpdateState.INCOMPATIBLE -> ChatPalette.DANGER
        UpdateState.FROZEN -> ChatPalette.WARN
        UpdateState.CHECKING -> ChatPalette.LABEL
    }

    fun stateLabel(state: UpdateState): String = when (state) {
        UpdateState.UP_TO_DATE -> "актуально"
        UpdateState.UPDATE_AVAILABLE -> "доступно обновление"
        UpdateState.UPDATE_STAGED -> "подготовлено к перезапуску"
        UpdateState.FROZEN -> "приостановлено"
        UpdateState.INCOMPATIBLE -> "несовместимо"
        UpdateState.BLOCKED -> "заблокировано"
        UpdateState.CHECKING -> "проверка"
        UpdateState.DOWNLOADING -> "загрузка"
        UpdateState.FAILED -> "ошибка"
        UpdateState.ROLLED_BACK -> "откат подготовлен к перезапуску"
    }

    /** [stateLabel] short enough for a catalog row; the full wording stays in the hover card. */
    fun shortStateLabel(state: UpdateState): String = when (state) {
        UpdateState.UPDATE_STAGED -> "ждёт перезапуска"
        UpdateState.FROZEN -> "пауза"
        UpdateState.ROLLED_BACK -> "откат ждёт перезапуска"
        else -> stateLabel(state)
    }

    fun operationLabel(operation: String): String = when (operation) {
        "CHECK" -> "проверка"
        "STAGE" -> "обновление"
        "ROLLBACK" -> "откат"
        "HEALTH" -> "проверка запуска"
        "HEALTH_ROLLBACK" -> "проверка отката"
        ComponentLedger.OPERATION_ADDED -> "установка"
        ComponentLedger.OPERATION_REMOVED -> "удаление"
        ComponentLedger.OPERATION_VERSION_CHANGED -> "смена версии"
        ComponentLedger.OPERATION_SETTINGS_CHANGED -> "настройки"
        else -> "состояние"
    }
}
