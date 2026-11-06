package org.pnfolder.pnlibrary.core.updates.history

import org.pnfolder.pnlibrary.api.updates.backup.BackupKind
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem
import java.util.Locale

/** How a saved file is shown to an administrator: "База данных «Игроки» · 12,5 МБ". */
object BackupItemText {
    fun kind(kind: BackupKind): String = when (kind) {
        BackupKind.CONFIGURATION -> "Конфигурация"
        BackupKind.DATABASE -> "База данных"
        BackupKind.PLAYER_DATA -> "Данные игроков"
        BackupKind.OTHER -> "Файл"
    }

    fun size(bytes: Long): String = when {
        bytes >= MB -> String.format(Locale.ROOT, "%.1f МБ", bytes.toDouble() / MB)
        bytes >= KB -> String.format(Locale.ROOT, "%.1f КБ", bytes.toDouble() / KB)
        else -> "$bytes Б"
    }

    fun describe(item: UpdateBackupItem): String {
        val title = if (item.name.isBlank()) kind(item.kind) else "${kind(item.kind)} «${item.name}»"
        return "${item.product}: $title · ${size(item.bytes)}"
    }

    private const val KB = 1024L
    private const val MB = 1024L * 1024
}
