package org.pnfolder.pnlibrary.core.downloads.dependencies

import org.pnfolder.pnlibrary.api.chat.notice.ChatTone
import org.pnfolder.pnlibrary.api.downloads.FileDownload
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.console.ConsoleCard
import org.pnfolder.pnlibrary.console.ConsoleTree
import org.pnfolder.pnlibrary.core.downloads.DownloadFailureInterpreter
import org.pnfolder.pnlibrary.core.text.ConsoleStyle
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata
import java.io.IOException
import java.nio.file.Paths

/** Reports dependency downloads to the console: failures with advice, staged files and dependencies confirmed after a restart. */
internal class DependencyDownloadReporter(
    private val logging: PlatformLogging,
    private val ownership: PlatformOwnerMetadata,
    /** The control command as typed on this platform: `pn`, or `pnproxy` on a proxy where `pn` belongs to the game servers. */
    private val root: String = "pn",
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
        val command = "$root deps install ${group.code}"
        val needed = group.items.filter { it.required }
        val optional = group.items.filterNot { it.required }
        val trees = listOf("Обязательные" to needed, "Необязательные" to optional).filter { it.second.isNotEmpty() }.map { (name, items) ->
            ConsoleTree.builder(name).also { tree -> items.forEach { item -> describe(tree, item) } }.build()
        }
        val next = ConsoleTree.builder("Что сделать").also { tree ->
            when {
                installingNow -> tree.child("Скачиваю сам, это займёт несколько секунд; потом перезапустите сервер")
                group.installable -> tree.child("Вставьте в консоль: $command").child("Или включите автозагрузку: $root deps auto on")
                else -> tree.child("Скачайте по ссылкам выше и положите в папку plugins")
            }
        }.build()
        ConsoleCard.builder(ConsoleStyle.theme(ChatTone.WARNING), "НЕ ХВАТАЕТ ПЛАГИНОВ")
            .mascot("o.o", group.owner, "нужны другие плагины")
            .blank().also { card -> trees.forEach { card.tree(it).blank() } }.tree(next).blank()
            .status(if (needed.isEmpty()) "Без них ${group.owner} работает, но без части возможностей" else "Без них ${group.owner} работает не полностью")
            .build()
            .send { logging.console(owner, it) }
        if (!installingNow && group.installable) {
            logging.log(owner, LogLevel.WARNING, "${group.owner}: не хватает ${group.items.joinToString { it.plugin }}. Установить: $command")
        }
    }

    /** One missing plugin: its name and version, why it is needed, how the file is checked, and where to get it. */
    private fun describe(tree: ConsoleTree.Builder, item: MissingDependency) {
        val name = if (item.title.equals(item.plugin, ignoreCase = true)) item.plugin else "${item.title} (${item.plugin})"
        tree.child("$name ${item.minimum}+")
        item.description?.let { tree.child("  $it") }
        if (item.noFileForServer) tree.child("  для этого сервера готового файла нет, скачайте вручную")
        item.variant?.let { tree.child("  выбран файл: $it") }
        val links = listOfNotNull(item.page) + item.sources
        links.forEach { tree.child("  $it") }
        if (item.sources.isNotEmpty()) {
            tree.child(if (item.checked) "  файл проверяется по SHA-256 автора" else "  сумма автора не задана, файл берётся с первой доступной ссылки")
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
