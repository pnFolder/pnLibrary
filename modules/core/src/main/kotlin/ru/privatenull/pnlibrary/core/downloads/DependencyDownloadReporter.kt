package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.downloads.FileDownload
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.console.ConsoleCard
import ru.privatenull.pnlibrary.console.ConsoleTheme
import ru.privatenull.pnlibrary.console.ConsoleTree
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.io.IOException
import java.nio.file.Paths

internal class DependencyDownloadReporter(private val platform: PlatformAdapter) {
    fun failure(owner: Any, error: Throwable, level: LogLevel) {
        val details = DownloadFailureInterpreter.explain(error)
        ConsoleCard.builder(errorTheme, "ЗАВИСИМОСТЬ НЕ ПОДГОТОВЛЕНА")
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
            .send { platform.console(owner, it) }

        if (error !is IllegalArgumentException && error !is IOException) {
            platform.log(owner, level, "Пакет загрузок не подготовлен: ${details.reason}", error)
        }
    }

    fun staged(owner: Any, declarations: List<FileDownload>) {
        val files = declarations.map { Paths.get(it.relativePath).fileName.toString() }
        ConsoleCard.builder(successTheme, "ЗАВИСИМОСТИ ПОДГОТОВЛЕНЫ")
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
            .send { platform.console(owner, it) }
    }

    fun connected(owner: Any, dependencies: List<DownloadedDependency>) {
        val tree = ConsoleTree.builder("Подключённые зависимости")
            .apply { dependencies.forEach { child("${it.name} ${it.version}") } }
            .build()
        ConsoleCard.builder(successTheme, "ЗАВИСИМОСТИ ПОДКЛЮЧЕНЫ")
            .mascot("^.^", ownerName(owner), "сервер успешно перезапущен")
            .blank()
            .tree(tree)
            .blank()
            .status("Зависимости загружены и работают")
            .build()
            .send { platform.console(owner, it) }
    }

    private fun ownerName(owner: Any): String = platform.ownerDetails(owner)["name"] ?: "pnLibrary"

    private companion object {
        val successTheme = ConsoleTheme("§6", "§a", "§f", "§8", "§r")
        val errorTheme = ConsoleTheme("§6", "§c", "§f", "§8", "§r")
    }
}
