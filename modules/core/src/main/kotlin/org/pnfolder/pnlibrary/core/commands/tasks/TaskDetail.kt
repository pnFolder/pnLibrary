package org.pnfolder.pnlibrary.core.commands.tasks

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import org.pnfolder.pnlibrary.common.time.DisplayZone
import java.time.Instant

/** Chat page with the details of one task. */
internal object TaskDetail {
    fun who(kit: ChatKit, task: TaskSnapshot, all: Collection<TaskSnapshot> = emptyList()): List<Component> {
        val lines = mutableListOf<Component>()
        lines += kit.blank()
        lines += kit.section("Кто и как")
        lines += kit.field("Плагин", kit.text(task.ownerName, ChatPalette.TEXT))
        TaskLabels.parent(task, all)?.let { lines += kit.field("Подзадача от", kit.text(it, ChatPalette.TEXT)) }
        TaskLabels.children(task, all).takeIf { it > 0 }?.let { lines += kit.field("Подзадач", kit.text("$it", ChatPalette.TEXT)) }
        task.source?.let { lines += kit.field("Класс", kit.text(it, ChatPalette.TEXT)) }
        task.key?.let { lines += kit.field("Ключ", kit.text(it, ChatPalette.TEXT)) }
        if (task.tags.isNotEmpty()) lines += kit.field("Теги", kit.text(task.tags.joinToString(", "), ChatPalette.SOFT))
        lines += kit.field("Выполняется", kit.text(TaskLabels.where(task), ChatPalette.TEXT))
        task.interval?.let { lines += kit.field("Повторяется", kit.text("каждые ${TaskLabels.span(it)}", ChatPalette.TEXT)) }
        lines += kit.field("Запусков", kit.text("${task.runCount}, пропущено ${task.skippedCount}", ChatPalette.TEXT))
        task.lastDuration?.let { lines += kit.field("Последний заняла", kit.text(TaskLabels.span(it), ChatPalette.TEXT)) }
        TaskLabels.lag(task)?.let { lines += kit.field("Опоздание", kit.text(it, ChatPalette.DANGER)) }
        return lines
    }

    fun whenLines(kit: ChatKit, task: TaskSnapshot): List<Component> {
        val lines = mutableListOf<Component>()
        lines += kit.blank()
        lines += kit.section("Когда")
        lines += kit.field("Создана", moment(kit, task.createdAt))
        task.nextRunAt?.let { lines += kit.field("Следующий запуск", moment(kit, it)) }
        task.lastStartedAt?.let { lines += kit.field("Последний запуск", moment(kit, it)) }
        task.lastCompletedAt?.let { lines += kit.field("Закончилась", moment(kit, it)) }
        lines += kit.field("Часовой пояс", kit.text(DisplayZone.zone.id, ChatPalette.FAINT))
        return lines
    }

    fun subtasks(kit: ChatKit, task: TaskSnapshot, all: Collection<TaskSnapshot>): List<Component> {
        val parent = task.parentId?.let { id -> all.firstOrNull { it.id == id } }
        val children = TaskLabels.childrenOf(task, all)
        if (parent == null && children.isEmpty()) return emptyList()
        val lines = mutableListOf<Component>(kit.blank(), kit.section("Связанные задачи"))
        parent?.let {
            lines += kit.field("Родитель", kit.text(TaskLabels.title(it), ChatPalette.ACCENT).clickEvent(ClickEvent.runCommand("/pn tasks view ${it.id.value}")))
        }
        children.forEach {
            lines += kit.field("Подзадача", kit.text("${TaskLabels.title(it)}, ${TaskLabels.label(it.status)}", ChatPalette.TEXT).clickEvent(ClickEvent.runCommand("/pn tasks view ${it.id.value}")))
        }
        return lines
    }

    fun rights(kit: ChatKit, task: TaskSnapshot): List<Component> {
        val lines = mutableListOf<Component>()
        lines += kit.blank()
        lines += kit.section("Права")
        lines += kit.field("Остановить", answer(kit, TaskLabels.stopAnswer(task)))
        lines += kit.field("Запустить сейчас", answer(kit, TaskLabels.runAnswer(task)))
        return lines
    }

    private fun moment(kit: ChatKit, at: Instant): Component =
        kit.text(TaskLabels.moment(at), ChatPalette.TEXT).append(kit.text("  ${TaskLabels.ago(at)}", ChatPalette.FAINT))

    private fun answer(kit: ChatKit, text: String): Component = kit.text(text, if (text == "да") ChatPalette.OK else ChatPalette.SOFT)
}
