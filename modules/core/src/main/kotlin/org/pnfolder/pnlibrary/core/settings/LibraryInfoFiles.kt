package org.pnfolder.pnlibrary.core.settings

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.io.TextFiles
import org.pnfolder.pnlibrary.updates.layout.InfoFileNames
import org.pnfolder.pnlibrary.updates.layout.LibraryPaths
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The two text files an administrator can open in the library's own folder: the help (`Помощь.txt`) and the overview of
 * every setting with its current value (`Настройки.txt`).
 *
 * The text is ours and lives here, in code. Both files are created again at every start and the overview also after every
 * change of a setting, so the links ([supportUrl], [repositoryUrl]) and the values are always those of the running library.
 * Nothing is cached anywhere else, and edits made by hand are lost.
 *
 * A file is rewritten only when its text changed, ignoring the line with the creation time: this way a start that changes
 * nothing leaves the files and their modification times alone. Writing never throws; the first failure is reported once
 * through [warning] and the library goes on.
 *
 * @param values the settings with their values now
 * @param version the library version shown at the end of both files
 * @param clock the source of the creation time
 */
internal class LibraryInfoFiles(
    private val paths: LibraryPaths,
    private val values: () -> List<SettingValue>,
    private val version: String,
    private val clock: Clock = Clock.systemUTC(),
    private val supportUrl: String = PnLibraryIdentity.SUPPORT_URL,
    private val repositoryUrl: String = PnLibraryIdentity.REPOSITORY_URL,
    private val warning: (String) -> Unit = {},
) {
    private val warned = AtomicBoolean(false)

    /** Writes both files; call it once the settings are loaded. */
    fun refresh() {
        refreshHelp()
        refreshSettings()
    }

    /** Writes the help file. */
    fun refreshHelp() = publish(paths.help, helpText())

    /** Writes the settings overview; call it after a setting changed. */
    fun refreshSettings() = publish(paths.settingsOverview, settingsText())

    /** The text of the help file. */
    fun helpText(): String = lines(
        "Помощь pnLibrary",
        "================",
        "",
        "pnLibrary — общая библиотека плагинов pnFolder для серверов Minecraft.",
        "Она ведёт настройки, обновления, диагностику и меню для всех наших плагинов.",
        "",
        "Нужна помощь или есть вопрос?",
        "  Напишите нам в Discord: $supportUrl",
        "",
        "Хотите улучшить плагин или заказать новый?",
        "  Идею, как улучшить плагин, присылайте туда же, в Discord: $supportUrl",
        "  Бесплатный плагин тоже можно заказать там же: просто напишите нам.",
        "",
        "Код и документация",
        "  GitHub: $repositoryUrl",
        "  Документация: $repositoryUrl/tree/main/docs",
        "",
        "Самые полезные команды",
        "  /pn updates          каталог плагинов и их обновления",
        "  /pn check            проверить, есть ли новые версии",
        "  /pn update <плагин>  подготовить обновление плагина",
        "  /pn settings         настройки библиотеки (все они перечислены в файле ${paths.settingsOverview.preferred})",
        "  /pn debug            собрать отчёт для поддержки",
        "  /pn support          ссылка на поддержку",
        "  /pn gui              меню",
        "",
        "Где библиотека хранит свои данные",
        "  В папке data/ рядом с этим файлом.",
        "",
        "Этот файл создаётся заново при каждом запуске, ваши правки не сохранятся.",
        "",
        *signature(),
    )

    /** The text of the settings overview. */
    fun settingsText(): String {
        val settings = values()
        return lines(
            "Настройки pnLibrary",
            "===================",
            "",
            "Как изменить настройку:",
            "  /pn settings list            все настройки со значениями",
            "  /pn settings get <ключ>      одна настройка подробно",
            "  /pn settings set <ключ> <значение>   изменить",
            "  /pn settings reset <ключ>    вернуть значение по умолчанию",
            "  Или в меню: /pn gui, раздел «Настройки», «Параметры библиотеки».",
            "Изменённые значения хранятся в data/settings.json, править его руками не нужно.",
            "",
            "Этот файл создаётся заново при каждом запуске и после каждого изменения настройки, ваши правки не сохранятся.",
            "",
            "Настроек: ${settings.size}",
            *settings.flatMap(::describe).toTypedArray(),
            "",
            *signature(),
        )
    }

    private fun describe(value: SettingValue): List<String> {
        val spec = value.spec
        return listOfNotNull(
            "",
            "${spec.key}  (${spec.title})",
            "  Сейчас: ${value.current}${if (value.changed) "  (изменено администратором)" else "  (по умолчанию)"}",
            "  По умолчанию: ${spec.default}",
            "  Тип: ${spec.kind.label}",
            spec.range().takeIf(String::isNotEmpty)?.let { "  Границы: $it" },
            spec.examples.takeIf(List<String>::isNotEmpty)?.let { "  Примеры: ${it.joinToString(", ")}" },
            "  Начнёт работать: ${spec.applies.label}",
            "  ${spec.description}",
        )
    }

    private fun signature(): Array<String> =
        arrayOf("${PnLibraryIdentity.NAME} $version", "$CREATED${TIME.format(clock.instant())} UTC")

    private fun lines(vararg text: String): String = text.joinToString("\n", postfix = "\n")

    /**
     * Writes [text] under the Russian name, or under the ASCII one when the server cannot create a file with a Russian
     * name: a JVM that runs with a non-UTF-8 file-name encoding (`sun.jnu.encoding`, common on Linux hosts with the C
     * locale) fails with [InvalidPathException] or [IOException] for such a name.
     */
    private fun publish(names: InfoFileNames, text: String) {
        try {
            try {
                writeIfChanged(paths.pluginFolder.resolve(names.preferred), text)
            } catch (error: Exception) {
                if (error !is InvalidPathException && error !is IOException) throw error
                writeIfChanged(paths.pluginFolder.resolve(names.ascii), text)
            }
        } catch (error: Exception) {
            if (warned.compareAndSet(false, true)) {
                warning("Не удалось записать файл ${names.preferred} в папку плагина: ${error.message}. Работе это не мешает.")
            }
        }
    }

    private fun writeIfChanged(target: Path, text: String) {
        val existing = if (Files.isRegularFile(target)) readOrNull(target) else null
        if (existing != null && withoutTime(existing) == withoutTime(text)) return
        AtomicFiles.write(target, text.toByteArray(UTF_8))
    }

    private fun readOrNull(file: Path): String? = try {
        TextFiles.read(file, UTF_8)
    } catch (_: IOException) {
        null
    }

    private fun withoutTime(text: String): List<String> = text.lines().filterNot { it.startsWith(CREATED) }

    private companion object {
        const val CREATED = "Создан: "
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)
    }
}
