# Встраиваемый установщик pnLibrary

Маленькая библиотека, которую плагин встраивает в свой jar. Сама она от pnLibrary не зависит, поэтому может
поставить pnLibrary раньше, чем плагин обратится к её API, и проверить, что версия подходит. Администратору не нужно
ставить pnLibrary вручную.

Работает на Bukkit (Spigot, Paper, Folia), BungeeCord и Velocity. На всех платформах скачивается один и тот же
`pnLibrary-<версия>-universal.jar` из релизов GitHub.

## Подключение

Выберите модуль своей платформы. Общая часть (`pnlibrary-bootstrap`) подтянется сама.

| Платформа | Зависимость | Класс |
|---|---|---|
| Bukkit | `io.github.pnfolder:pnlibrary-bootstrap-bukkit` | `org.pnfolder.pnlibrary.bukkit.bootstrap.PnLibraryBootstrap` |
| BungeeCord | `io.github.pnfolder:pnlibrary-bootstrap-bungee` | `org.pnfolder.pnlibrary.bungee.bootstrap.PnLibraryBootstrap` |
| Velocity | `io.github.pnfolder:pnlibrary-bootstrap-velocity` | `org.pnfolder.pnlibrary.velocity.bootstrap.PnLibraryBootstrap` |

```kotlin
dependencies {
    implementation("io.github.pnfolder:pnlibrary-bootstrap-bukkit:<версия>")   // или -bungee, -velocity
}

tasks.shadowJar {
    // перенесите пакеты установщика к себе, чтобы не столкнуться с копией другого плагина
    relocate("org.pnfolder.pnlibrary.bootstrap", "your.plugin.libs.pnlibrary.bootstrap")
    relocate("org.pnfolder.pnlibrary.bukkit.bootstrap", "your.plugin.libs.pnlibrary.bukkit.bootstrap")
    relocate("org.pnfolder.pnlibrary.console", "your.plugin.libs.pnlibrary.console")
    relocate("com.google.gson", "your.plugin.libs.gson")
}
```

## Вызов

Вызов одинаковый на всех платформах: `PnLibraryBootstrap.ensureInstalled(..., "минимальная версия")`. Он возвращает
`true`, только если подходящая pnLibrary уже работает. Вызывайте его до любого обращения к pnLibrary.

**Bukkit** (Kotlin, на Java то же самое):

```kotlin
override fun onLoad() {
    if (!PnLibraryBootstrap.ensureInstalled(this, "1.0.0")) server.pluginManager.disablePlugin(this)
}
```

В `plugin.yml`: `softdepend: [pnLibrary]`, а не `depend`. С жёсткой зависимостью Bukkit не загрузит плагин раньше,
чем установщик успеет отработать.

**BungeeCord:**

```kotlin
private var ready = false

override fun onLoad() {
    ready = PnLibraryBootstrap.ensureInstalled(this, "1.0.0")
}

override fun onEnable() {
    if (!ready) return
    // дальше можно пользоваться pnLibrary
}
```

В `bungee.yml`: `softDepends: [pnLibrary]`.

**Velocity:**

```kotlin
@Plugin(id = "myplugin", dependencies = [Dependency(id = "pnlibrary", optional = true)])
class MyPlugin @Inject constructor(private val proxy: ProxyServer) {
    @Subscribe
    fun onInitialize(event: ProxyInitializeEvent) {
        if (!PnLibraryBootstrap.ensureInstalled(proxy, this, "1.0.0")) return
        // дальше можно пользоваться pnLibrary
    }
}
```

Классы, которые используют pnLibrary, не должны попадать в путь вызова установщика, пока он не вернул `true`.

## Что происходит

| Ситуация | Bukkit | BungeeCord, Velocity |
|---|---|---|
| pnLibrary нет | скачивает, проверяет и сразу запускает; `true` | скачивает и проверяет; пишет «нужен перезапуск»; `false`. После перезапуска прокси плагин запустится |
| pnLibrary есть и версия подходит | `true` | `true` |
| pnLibrary старее нужной | карточка «PNLIBRARY УСТАРЕЛА» с командой обновления; `false` | то же |
| pnLibrary есть, но не запустилась | карточка с причиной; `false` | то же |
| нет доступа к GitHub или файл не прошёл проверку | карточка с причиной и ссылкой на ручную загрузку; `false` | то же |

Прокси не умеют загружать плагины на ходу, поэтому там нужен перезапуск. Есть два варианта:

- **Сам, с обратным отсчётом.** Включите `restartCountdown`:

  ```kotlin
  val options = BootstrapOptions.builder("1.0.0").restartCountdown(10).build()
  PnLibraryBootstrap.ensureInstalled(proxy, this, options)
  ```

  Консоль и игроки в сети видят «Перезапуск прокси через 10 с», потом каждую секунду с пятой: 5, 4, 3, 2, 1. Затем
  прокси останавливается, а игроки видят причину. Обратно его поднимает панель хостинга или скрипт запуска:
  сам себя процесс запустить не может. Если автоперезапуска на хостинге нет, прокси останется выключенным.
- **Вручную** (по умолчанию). В карточке установки есть готовая команда остановки, её можно скопировать в консоль,
  когда удобно: `end` на BungeeCord, `shutdown` на Velocity.

На Bukkit перезапуск не нужен, параметр там ничего не делает.

Скачанную pnLibrary установщик сам не
обновляет: если версия старая, он говорит, как её обновить (`/pn update pnLibrary` на Bukkit, `pnupdate` в консоли
прокси).

Только проверить, без установки: `PnLibraryBootstrap.ensureCompatible(...)` с теми же аргументами.

## Что проверяется

- Берётся последний стабильный релиз, а если он старее минимальной версии, то релиз ровно этой версии.
- Скачивается только `universal.jar` по HTTPS с указанными в GitHub размером и SHA-256.
- Размер и SHA-256 скачанного файла сверяются.
- Внутри jar проверяется дескриптор своей платформы (`plugin.yml`, `bungee.yml` или `velocity-plugin.json`).
- Файл кладётся в `plugins` атомарным переносом. Если на Bukkit запуск не удался, всё возвращается как было.

## Настройки

```kotlin
val options = BootstrapOptions.builder("1.0.0")
    .repository("pnFolder", "pnLibrary")     // откуда брать релизы
    .maximumBytes(256L * 1024 * 1024)        // предел размера файла
    .build()
PnLibraryBootstrap.ensureInstalled(this, options)
```

Сравнить версии самому: `BootstrapVersion.isAtLeast("1.2.0", "1.0.0")`.
