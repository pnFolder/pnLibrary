package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.downloads.FileDownload
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.console.ConsoleCard
import ru.privatenull.pnlibrary.console.ConsoleTree
import ru.privatenull.pnlibrary.core.ui.ChatTone
import ru.privatenull.pnlibrary.core.ui.ConsoleStyle
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnerMetadata
import java.io.IOException
import java.nio.file.Paths

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

    private fun ownerName(owner: Any): String = ownership.ownerDetails(owner)["name"] ?: "pnLibrary"
}
