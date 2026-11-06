// Пример: все настройки при регистрации модуля. Файл не входит в сборку, это образец для копирования.
// Всё необязательное можно удалить: достаточно registerModule("main") { }.
package example

import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.dependencies.DownloadPolicy
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer
import org.pnfolder.pnlibrary.api.downloads.DownloadDestination
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.remote.DenyAction
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import java.time.Duration
import java.util.function.Supplier

class PnLibraryRegistrationExample(private val plugin: JavaPlugin) : AutoCloseable {

    private val library = PnLibraryProvider.getOrNull() ?: error("pnLibrary недоступна")

    // Версия библиотеки: плагин не стартует на старой.
    init { require(library.isAtLeastVersion("0.1.0-dev.1")) { "Нужна pnLibrary не ниже 0.1.0-dev.1" } }

    private val registration = library.plugins.register(plugin)

    // Свои тексты ошибок команд (тип ошибки берётся из api.plugin.CommandErrors):
    // registration.onCommandError(SomeCommandError::class.java, "Нет прав")

    val context: ModuleContext = try {
        registration.registerModule("main") { setup ->

            // 1. Метаданные: если автоопределение из plugin.yml не подходит.
            setup.metadata { it.name("MyPlugin").version(plugin.description.version).authors("pnFolder") }

            // 2. Переключатели.
            setup.options { it.placeholderApi(true) }   // публиковать плейсхолдеры в PlaceholderAPI

            // 3. Зависимости.
            setup.dependencies { deps ->
                // Компонент pnFolder.
                deps.product("pnclans-core") { p ->
                    p.minimumVersion("1.0.0")
                        .maximumVersionExclusive("2.0.0")
                        .github("pnFolder", "pnClans")
                        .required(true)
                        .downloadPolicy(DownloadPolicy.AUTOMATIC)   // MANUAL | AUTOMATIC | FORCED
                }
                // Чужой плагин.
                deps.plugin("Vault", "1.7") { e ->
                    e.downloadPage("https://www.spigotmc.org/resources/vault.34315/")
                        .required(false)
                        .verifyPluginId(true)
                        .verifyVersion(true)
                        .downloadPolicy(DownloadPolicy.MANUAL)
                    // Или скачивать самим: .artifact("https://.../Vault.jar", 123456L, "sha256...").automaticDownload(true)
                }
            }

            // 4. Обновления плагина.
            setup.updates { u ->
                u.repository("pnFolder", "MyPlugin")
                    .branch("main")                       // или .tag("v1.0.0") / .commit("abc1234")
                    .catalogPath(".pnlibrary")            // папка с releases.json
                    .channel(Supplier { UpdateChannel.STABLE })   // читается при каждой проверке, можно из конфига
                    .automaticDownload(true)
                    .supportedApi(1, 1)                   // какой API умеет ЭТА сборка
                    .artifact("(?i)^MyPlugin-.*-java21\\.jar$", 21, 24)   // шаблон имени jar, Java от и до
                    .artifact("(?i)^MyPlugin-.*-java25\\.jar$", 25)
                    // .exactArtifact("MyPlugin.jar", 8)  // точное имя вместо шаблона
                    .configurationBackup(plugin.dataFolder.toPath(), "config.yml", "messages.yml")
            }

            // 5. Скачивание внешних файлов.
            setup.downloads(plugin.dataFolder.toPath()) { d ->
                d.file("geoip") { f ->
                    f.url("https://example.com/geoip.mmdb")
                        .required(false)
                        .automaticDownload(true)
                        .integrity(1_000_000L, "sha256...")
                        .destination(DownloadDestination.DATA_FOLDER, "geoip.mmdb")   // или CACHE
                        .java(8)
                    // .platform(PlatformType.X) если файл только для одной платформы
                }
            }

            // 6. Диагностика: что попадёт в отчёт поддержки.
            setup.diagnostics(
                plugin.dataFolder.toPath(),
                DiagnosticContainer.builder("myplugin")
                    .snapshot(Supplier { mapOf("version" to plugin.description.version, "players" to plugin.server.onlinePlayers.size) })
                    .value("storage", "sqlite")
                    .configuration(
                        DiagnosticConfiguration.builder("config.yml")
                            .exclude("database.host")                                        // не показывать путь целиком
                            .secretKeyRegex("(?i).*(password|token|secret|api[-_]?key).*")   // скрыть значения по имени ключа
                            .redactValueRegex("(?i)^Bearer .+")                              // скрыть значения по виду
                            .build(),
                    )
                    .configuration("messages.yml")   // без скрытия
                    .build(),
            )

            // 7. Метрики.
            setup.metrics { m ->
                m.enabled(true)
                    .bStats(12345)                       // или bStats(платформа, id) / bStats(карта)
                    .fastStats("токен")
                    .charts { c ->
                        c.simplePie("storage") { "sqlite" }
                        c.advancedPie("modes") { mapOf("a" to 3, "b" to 1) }
                        c.drilldownPie("versions") { mapOf("1.21" to mapOf("paper" to 10)) }
                        c.singleLineChart("online") { plugin.server.onlinePlayers.size }
                        c.multiLineChart("lines") { mapOf("x" to 1, "y" to 2) }
                        c.simpleBarChart("bars") { mapOf("x" to 1) }
                        c.advancedBarChart("adv") { mapOf("x" to intArrayOf(1, 2, 3)) }
                    }
            }

            // 8. Удалённая политика: разработчик может запретить запуск плагина.
            // Политика это ОДИН .java-файл (до 1 МБ) по https:// или file:, сервер компилирует его своим javac,
            // а на JRE без компилятора библиотека сама скачивает ECJ. Другие расширения библиотека отвергает.
            setup.remotePolicy(
                RemotePolicySettings.builder()
                    .source("https://raw.githubusercontent.com/pnFolder/MyPlugin/main/policy/MyPluginPolicy.java")
                    .checkEvery(Duration.ofHours(6))            // не меньше минуты
                    .startupTimeout(Duration.ofSeconds(10))
                    .onDeny(DenyAction.DISABLE_PLUGIN)          // или DISABLE_MODULE
                    .onError(PolicyErrorAction.KEEP_RUNNING)    // DENY | DENY_UNTIL_CHECKED | KEEP_RUNNING
                    .support("https://discord.gg/...")
                    .value("edition", "free")                   // значения, которые получит код политики
                    .build(),
            )
        }
    } catch (failure: RuntimeException) {
        registration.close()
        throw failure
    }

    override fun close() = registration.close()   // вызвать в onDisable
}
