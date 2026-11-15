package org.pnfolder.pnlibrary.core.downloads

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.downloads.FileDownload
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.console.ConsoleCard
import org.pnfolder.pnlibrary.console.ConsoleTree
import org.pnfolder.pnlibrary.core.ui.ConsoleStyle
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata
import java.io.IOException
import java.nio.file.Paths

/** Reports dependency downloads to the console: failures with advice, staged files and dependencies confirmed after a restart. */
internal class DependencyDownloadReporter(
    private val logging: PlatformLogging,
    private val ownership: PlatformOwnerMetadata,
) {
    fun failure(owner: Any, error: Throwable, level: LogLevel) {
        val details = DownloadFailureInterpreter.explain(error)
        ConsoleCard.builder(ConsoleStyle.theme(ChatTone.FAILURE), "ЗАВИСИМОСТЬ НЕ ПОДГОТОВЛЕНА")
            .mascot("x.x", ownerName(owner), details.headline)
            .blank()
            .tree(ConsoleTree.builder("Причина").child(details.reason).build())
            .blank()
            .tree(ConsoleTree.builder("Что может сделать пользователь").child(details.userAction).build())
            .blank()
            .tree(ConsoleTree.builder("Что сообщить разработчику").child(details.developerAction).build())
            .blank()
            .status("Файл не установлен")
            .build()
            .send { logging.console(owner, it) }

        if (error !is IllegalArgumentException && error !is IOException) {
            logging.log(owner, level, "Пакет загрузок не подготовлен: ${details.reason}", error)
        }
    }

    /** Tells the administrator what is missing, where to get it, and how to have it installed. */
    fun missing(owner: Any, group: MissingGroup, installingNow: Boolean) {
        val command = "pn deps install ${group.code}"
        val wanted = ConsoleTree.builder("Не хватает").also { tree ->
            group.items.forEach { item ->
                val links = listOfNotNull(item.page) + item.sources
                tree.child("${item.plugin} ${item.minimum}+")
                links.forEach { tree.child("  $it") }
            }
        }.build()
        val next = ConsoleTree.builder("Что сделать").also { tree ->
            when {
                installingNow -> tree.child("Скачиваю сам, это займёт несколько секунд; потом перезапустите сервер")
                group.installable -> tree.child("Вставьте в консоль: $command").child("Или включите автозагрузку: pn deps auto on")
                else -> tree.child("Скачайте по ссылкам выше и положите в папку plugins")
            }
        }.build()
        ConsoleCard.builder(ConsoleStyle.theme(ChatTone.WARNING), "НЕ ХВАТАЕТ ПЛАГИНОВ")
            .mascot("o.o", group.owner, "нужны другие плагины")
            .blank().tree(wanted).blank().tree(next).blank()
            .status("Без них ${group.owner} работает не полностью")
            .build()
            .send { logging.console(owner, it) }
        if (!installingNow && group.installable) {
            logging.log(owner, LogLevel.WARNING, "${group.owner}: не хватает ${group.items.joinToString { it.plugin }}. Установить: $command")
        }
    }

    fun staged(owner: Any, declarations: List<FileDownload>) {
        val files = declarations.map { Paths.get(it.relativePath).fileName.toString() }
        ConsoleCard.builder(ConsoleStyle.theme(ChatTone.SUCCESS), "ЗАВИСИМОСТИ ПОДГОТОВЛЕНЫ")
            .mascot("^.^", ownerName(owner), "файлы готовы к запуску")
            .blank()
            .tree(ConsoleTree.builder("Скачанные зависимости").apply { files.forEach(::child) }.build())
            .blank()
            .tree(ConsoleTree.builder("Что дальше")
                .child("Перезапусти сервер")
                .child("Плагины загрузятся при следующем запуске")
                .build())
            .blank()
            .status("Загрузка завершена")
            .build()
            .send { logging.console(owner, it) }
    }

    fun connected(owner: Any, dependencies: List<DownloadedDependency>) {
        val tree = ConsoleTree.builder("Подключённые зависимости")
            .apply { dependencies.forEach { child("${it.name} ${it.version}") } }
            .build()
        ConsoleCard.builder(ConsoleStyle.theme(ChatTone.SUCCESS), "ЗАВИСИМОСТИ ПОДКЛЮЧЕНЫ")
            .mascot("^.^", ownerName(owner), "сервер успешно перезапущен")
            .blank()
            .tree(tree)
            .blank()
            .status("Зависимости загружены и работают")
            .build()
            .send { logging.console(owner, it) }
    }

    private fun ownerName(owner: Any): String = ownership.ownerDetails(owner)["name"] ?: PnLibraryIdentity.NAME
}
