# Подключение pnLibrary к pnFolder-плагину

На сервер устанавливается один платформенный runtime: `pnLibrary-bukkit`,
`pnLibrary-bungee` или `pnLibrary-velocity`. Плагин-потребитель подключает только
`pnlibrary-api` как `compileOnly`; API нельзя встраивать или relocate-ить в его JAR.

## Gradle

```kotlin
dependencies {
    compileOnly("ru.privatenull:pnlibrary-api:2.0.0-beta.5")
}
```

Если используются Bukkit-специфичные меню или определение текущей версии ядра,
добавьте `compileOnly("ru.privatenull:pnlibrary-bukkit:2.0.0-beta.5") { isTransitive = false }`.

Для Bukkit/Paper добавьте в `plugin.yml`:

```yaml
depend: [pnLibrary]
```

Получение общего runtime и регистрация возможностей плагина:

```kotlin
private lateinit var pn: PnLibrary
private var metrics: PluginMetrics? = null
private var diagnostics: DiagnosticRegistration? = null
private var updates: UpdateRegistration? = null
private lateinit var tasks: TaskScope

override fun onEnable() {
    pn = PnLibraryProvider.get()
    tasks = pn.tasks.scope(this)

    pn.logging.box(this, "pnMarket")
        .ok("Конфигурация", "загружена")
        .ok("Команды", "зарегистрированы")
        .show()

    // ID проекта pnMarket на bStats. У каждого продукта собственный ID.
    metrics = pn.metrics.open(this, 12345)
        .simplePie("storage_type") { database.type }
        .advancedPie("features") {
            mapOf("auctions" to 1, "delivery" to 1)
        }

    diagnostics = pn.diagnostics.register(name, dataFolder.toPath(),
        DiagnosticContainer.builder("auction")
        .snapshot(Supplier {
            mapOf("activeLots" to auction.activeLots, "cacheSize" to auction.cacheSize)
        })
        .configuration(DiagnosticConfiguration.file("config.yml")
            .secretKeyRegex("(?i).*(password|token|secret|webhook).*")
            .build())
        .configuration("gui.yml")
        .build())

    updates = pn.updates.register(this,
        PluginUpdateRequest.builder()
            .repository("pnFolder", "pnMarket")
            .channel(UpdateChannel.STABLE)
            .automaticDownload(settings.autoUpdate)
            .artifact("(?i)^pnMarket-.*-paper-.*-java21\\.jar$", 21, 24)
            .artifact("(?i)^pnMarket-.*-paper-.*-java25\\.jar$", 25)
            .build())
}

override fun onDisable() {
    tasks.close()
    updates?.close()
    diagnostics?.close()
    metrics?.close()
}
```

Текущую версию передавать не нужно. pnLibrary получает её из платформенного
описания загруженного плагина (`plugin.yml`, `bungee.yml` или
`velocity-plugin.json`), куда Gradle подставляет `project.version` во время
сборки. pnLibrary сравнивает Java сервера с диапазонами артефактов и выбирает
самую новую совместимую сборку: например, Java 21–24 получает java21-JAR, а
Java 25 и новее — java25-JAR. Проверка выполняется сразу после регистрации и затем
каждые 30 минут; файл не больше 512 МБ принимается только при наличии совпавшей
SHA-256 из `checksums.sha256` и корректного дескриптора платформы.
Повторное уведомление об одной версии и одном состоянии выводится не чаще раза в
6 часов, поэтому частая проверка не засоряет консоль.

`automaticDownload(false)` отключает только фоновую загрузку. Проверка,
консольные и игровые уведомления, `/pn status`, `/pn updates` и ручное действие
`/pn update <плагин>` продолжают работать. Discord берётся из единого
`PnLibraryBrand.SUPPORT_URL`; повторять ссылку в регистрации каждого плагина не нужно.

`projectId` берётся на странице проекта bStats. pnLibrary сохраняет штатную
глобальную настройку opt-out bStats и только управляет созданием, диаграммами и
закрытием платформенной сессии. API поддерживает `SimplePie`, `AdvancedPie`,
`DrilldownPie`, `SingleLineChart`, `MultiLineChart`, `SimpleBarChart` и
`AdvancedBarChart`.

На Bukkit сервис можно получить и без статического provider:

```kotlin
val pn = server.servicesManager.load(PnLibrary::class.java)
    ?: error("pnLibrary service is unavailable")
```
