# pnLibrary

`pnLibrary` — единый runtime и API для плагинов pnFolder. Владелец сервера устанавливает один JAR для своей платформы, а pnMarket, pnClans и другие плагины подключаются к общему экземпляру библиотеки.

В pnLibrary находятся:

- единая команда `/pndebug`;
- сбор и шифрование диагностических отчётов;
- регистрация состояний, предупреждений и ошибок других плагинов;
- безопасный сбор разрешённых конфигураций;
- единое красивое логирование и MBox;
- единая система пользовательских событий для всех платформ;
- собственный Kotlin API метрик поверх официальных классов bStats;
- адаптеры Bukkit/Paper/Leaf/Folia, BungeeCord и Velocity.

## Как это устроено

```text
Сервер или прокси
├── pnLibrary-<platform>.jar     один общий runtime
├── pnMarket.jar ───────────────┐
├── pnClans.jar ────────────────┼── pnlibrary-api (compileOnly)
└── другие pnFolder-плагины ────┘
```

Каждый процесс имеет собственный runtime. Если сеть состоит из Velocity и трёх Paper-серверов, pnLibrary устанавливается на прокси и на каждый Paper-сервер. Внутри одного процесса регистрируется только одна команда `/pndebug`.

## Модули

| Модуль | Назначение | JVM target |
|---|---|---:|
| `pnlibrary-api` | Публичные интерфейсы для pnFolder-плагинов | 8 |
| `pnlibrary-core` | Диагностика, шифрование, отчёты, logging и lifecycle | 8 |
| `pnlibrary-bstats-base` | Неизменённые общие классы официального bStats | 8 |
| `pnlibrary-bukkit` | Bukkit, Spigot, Paper, Purpur, Leaf и Folia | 8 |
| `pnlibrary-bungee` | BungeeCord и совместимые прокси | 8 |
| `pnlibrary-velocity` | Velocity | 17 |
| `pnlibrary-distribution` | Сборка готовых платформенных JAR | — |

Краткая карта кода для разработчика: [HELP-README.md](HELP-README.md).

Проект собирается на JDK 17 или новее. Bukkit, включая Folia-ветку планировщика,
и BungeeCord получают байткод Java 8; Velocity — Java 17. Жёсткой привязки к
конкретной установленной JDK нет.

## Установка

Сборка:

```text
gradlew.bat clean test :pnlibrary-distribution:build
```

Готовые файлы находятся в `pnlibrary-distribution/build/libs`:

- `pnLibrary-bukkit-2.0.0-beta.6.jar`;
- `pnLibrary-bungee-2.0.0-beta.6.jar`;
- `pnLibrary-velocity-2.0.0-beta.6.jar`;
- `pnLibrary-api-2.0.0-beta.6.jar` и sources для разработчиков.

Положите один подходящий JAR в папку `plugins` и полностью перезапустите сервер.

При первом запуске создаётся `plugins/pnLibrary/config.yml`. В нём настраиваются
загрузка отчётов, режим шифрования, сбор конфигураций и журналов, cooldown и сроки
хранения. Неизвестные параметры и некорректные значения останавливают запуск с
понятной ошибкой, чтобы опечатка не меняла политику приватности незаметно.
Полная таблица находится в [docs/RUNTIME_CONFIGURATION_RU.md](docs/RUNTIME_CONFIGURATION_RU.md).

## Подключение API

Сначала можно опубликовать API в локальный Maven-кэш:

```text
gradlew.bat :pnlibrary-api:publishToMavenLocal
```

В плагине:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    compileOnly("ru.privatenull:pnlibrary-api:2.0.0-beta.6")
}
```

Для Bukkit-специфичных API (`PnMenus`, `BukkitMinecraftVersion`) дополнительно:

```kotlin
compileOnly("ru.privatenull:pnlibrary-bukkit:2.0.0-beta.6") { isTransitive = false }
```

API нельзя встраивать через `implementation`, Shadow или relocation: его предоставляет установленная pnLibrary.

Bukkit/Paper, `plugin.yml`:

```yaml
depend: [pnLibrary]
```

BungeeCord, `bungee.yml`:

```yaml
depends: [pnLibrary]
```

Velocity:

```kotlin
@Plugin(
    id = "pnmarket",
    name = "pnMarket",
    version = "1.0.5",
    dependencies = [Dependency(id = "pnlibrary")]
)
class PnMarketPlugin
```

## Получение pnLibrary

Bukkit:

```kotlin
val pn = server.servicesManager.load(PnLibrary::class.java)
    ?: error("pnLibrary не загрузилась")
```

BungeeCord и Velocity:

```kotlin
val pn = PnLibraryProvider.get()
```

Для необязательной интеграции доступен `PnLibraryProvider.getOrNull()`.

## Логирование и MBox

```kotlin
val log = pn.logging.logger(this, "pnMarket")

log.info("Загрузка аукциона")
log.success("Аукцион загружен")
log.warning("Vault не найден")
log.error("Не удалось подключиться к базе", exception)
```

Красивый блок запуска:

```kotlin
pn.logging.box(this, "pnMarket 1.0.5")
    .ok("Конфигурация", "загружена")
    .ok("Команды", "зарегистрированы")
    .skip("PlaceholderAPI", "не установлен")
    .fail("Database", "подключение не установлено", exception)
    .show()
```

MBox работает на всех платформах:

- Bukkit/Paper/Leaf/Folia — через logger Bukkit-плагина;
- BungeeCord — через logger Bungee-плагина;
- Velocity — через штатный SLF4J logger.

## Метрики

Плагин передаёт собственный project ID со страницы bStats:

```kotlin
private var metrics: PluginMetrics? = null

metrics = pn.metrics.open(this, projectId = 12345)
    .simplePie("storage_type") { database.type }
    .singleLineChart("active_lots") { auction.activeLots }
    .advancedPie("features") {
        mapOf("delivery" to 1, "favorites" to 1)
    }
```

Поддерживаются `SimplePie`, `AdvancedPie`, `DrilldownPie`, `SingleLineChart`, `MultiLineChart`, `SimpleBarChart` и `AdvancedBarChart`.

В конфигурации pnFolder-плагина нет локального параметра `metrics.enabled`: указание положительного project ID запускает сессию. Официальный глобальный opt-out bStats сохраняется согласно требованиям сервиса.

Наш публичный API написан на Kotlin. Официальные Java-классы bStats хранятся внутри проекта без функциональных изменений и при сборке переносятся в `ru.privatenull.pnlibrary.libs.bstats`.

## Диагностика

`/pndebug` всегда собирает безопасный системный снимок и зарегистрированные
контейнеры. Флаг `--config` добавляет разрешённые конфигурации, `--logs` —
ограниченную историю предупреждений и ошибок, записанных через `pn.logging`, а
`--full` включает оба набора. Для `all` данные собираются по всем плагинам с
явным указанием владельца каждого файла.

## Кроссплатформенные задачи

Каждый плагин получает собственный scope. Таймер работает внутри pnLibrary, а
само действие передаётся правильному планировщику платформы: глобальному или
entity scheduler на Folia, основному потоку Bukkit/Paper либо планировщику
прокси. На Bukkit scope автоматически закрывается при `PluginDisableEvent`.

```kotlin
private val tasks = pn.tasks.scope(this)

tasks.global(Runnable { reloadMenus() })
tasks.async(Runnable { database.cleanup() })
tasks.entity(player, Runnable { player.openInventory(menu) })
tasks.later(Duration.ofSeconds(5), Runnable { refreshCache() })
tasks.repeat(Duration.ZERO, Duration.ofMinutes(1), Runnable { refreshCache() })
tasks.repeatEntity(player, Duration.ZERO, Duration.ofSeconds(1), Runnable { updateHud(player) })

tasks.asyncThen(
    Supplier { repository.load() },
    Consumer { result -> applyOnServerThread(result) },
    Consumer { error -> report(error) },
)

override fun onDisable() {
    tasks.close()
}
```

`TaskHandle.cancel()` останавливает отдельную задачу, `TaskScope.cancelAll()` —
все задачи плагина. Ошибка одного callback перехватывается, журналируется с
владельцем и не останавливает остальные повторяющиеся задачи.

## Кроссплатформенные события

Собственные события плагинов не должны зависеть от Bukkit, BungeeCord или
Velocity. Событие реализует `PnEvent`, а подписки хранятся в scope владельца:

```kotlin
data class ClanCreatedEvent(val clanId: String) : PnEvent

private val events = pn.events.scope(this)

events.subscribe<ClanCreatedEvent> { event ->
    logger.info("Создан клан ${event.clanId}")
}

events.publish(ClanCreatedEvent("knights"))
```

Обработка синхронная и выполняется в вызывающем потоке. Приоритеты, порядок,
отмена через `CancellablePnEvent` и изоляция ошибок одинаковы на всех платформах.
`events.close()` снимает сразу все подписки плагина. Нативные игровые события
адаптируются на границе платформенного модуля только там, где это действительно
нужно; прикладные события остаются полностью независимыми от сервера.

## Инвентари Bukkit, Paper и Folia

pnLibrary регистрирует один общий обработчик инвентарей. Каждый открытый GUI имеет
закрытый `InventoryHolder` с уникальным идентификатором сессии: заголовок меню не
используется для определения GUI. Плагины получают сервис
через `PnMenus.get()`, а владельца передают при открытии. Поддерживаются сундук,
наковальня, воронка, раздатчик, выбрасыватель и верстак. Реализация собрана против
Spigot 1.8.8, не использует NMS и работает на новых Paper/Folia через тот же JAR.

```kotlin
private val menus by lazy { PnMenus.get() }

val menu = Menus.chest("Кланы")
    .rows(3)
    .border(glass)
    .button(13, clanIcon, MenuClickHandler { click ->
        click.player.sendMessage("Открываем клан")
        click.close()
    })
    .onClose(MenuCloseHandler { close -> saveDraft(close.session.player) })
    .build()

menus.open(this, player, menu)
```

Динамическое содержимое задаётся через `render`: оно вызывается при открытии и
после `session.refresh()`. `refreshAfter(Duration)` выполняется через общий
планировщик pnLibrary и поэтому безопасен для Folia.

```kotlin
val anvil = Menus.anvil("Название клана")
    .editable(0, paperWithCurrentName)
    .button(2, confirmItem, MenuClickHandler { click ->
        renameClan(click.player, click.renameText.orEmpty())
        click.close()
    })
    .build()
```

По умолчанию запрещены клики, shift-click и drag, способные переносить предметы
в защищённые слоты. Разрешить ввод можно только для нужного слота через
`editable(slot)`. При выключении плагина все принадлежащие ему меню закрываются и
удаляются из памяти. Обработчики представлены SAM-интерфейсами, поэтому Kotlin
лямбды не создают зависимости от конкретного экземпляра Kotlin runtime.

## Версия Minecraft

В общем API-модуле есть единый `MinecraftVersion` со всеми известными версиями от
1.8 до 26.2. Он читает нативный `getMinecraftVersion()` новых ядер, а на старых
использует `Bukkit.getBukkitVersion()`. Незнакомый будущий релиз безопасно
возвращает `UNKNOWN`, при этом исходная строка доступна через `rawCurrent()`.

```kotlin
val version = BukkitMinecraftVersion.current()

if (version.isAtLeast(MinecraftVersion.V1_20_5)) enableDataComponents()
if (version.isBetween(MinecraftVersion.V1_8_8, MinecraftVersion.V1_12_2)) {
    enableLegacyInventoryAdapter()
}

logger.info("Minecraft: ${version.text}; raw=${BukkitMinecraftVersion.rawCurrent()}")
```

Те же значения кэшируются в `ServerCapabilities.minecraftVersion` и
`ServerCapabilities.rawMinecraftVersion`.

Полное описание сравнений, открытых и закрытых диапазонов находится в
[docs/MINECRAFT_VERSIONS_RU.md](docs/MINECRAFT_VERSIONS_RU.md).

## Code-first конфигурации

`CodeFirstYaml<T>` автоматически добавляет новые поля типизированной модели в
старый YAML, включая вложенные секции и комментарии. Пользовательские значения,
неизвестные ключи и существующие комментарии сохраняются. Изменённый файл
проходит декодирование и проверку до атомарной записи; перед синхронизацией
создаётся резервная копия.

Полное подключение Kaml, валидация, сохранение и Java-пример описаны в
[docs/CONFIGURATION_RU.md](docs/CONFIGURATION_RU.md).

## Обновления

При первом запуске создаётся `plugins/pnLibrary/updates.yml`:

```yaml
# stable — только стабильные релизы (рекомендуется).
# beta   — стабильные и beta-релизы.
# alpha  — все релизы, включая экспериментальные alpha.
# Автоматическую загрузку можно отключить; проверка и уведомления останутся активными.
channel: stable
auto-download: true
```

pnLibrary проверяет релизы `pnFolder/pnLibrary` при запуске и каждые 30 минут,
выбирает JAR текущей платформы, ограничивает загрузку размером 512 МБ, сверяет
SHA-256, проверяет дескриптор плагина и помещает
обновление в каталог `plugins/update`. Обновление применяется после перезапуска.
Одинаковое найденное обновление проверяется по расписанию, но повторное сообщение
о нём выводится не чаще одного раза в 6 часов. Смена состояния, например успешная
ручная загрузка, показывается сразу.
При `auto-download: false` библиотека продолжает проверять релизы, уведомлять
консоль и администраторов, а ручная загрузка остаётся доступна через `/pn update`.
На Bukkit/Paper/Folia `/pn restart` показывает число игроков и требует отдельное
подтверждение в течение 30 секунд; только после него выполняется перезапуск.

```kotlin
private var diagnostics: DiagnosticRegistration? = null

diagnostics = pn.diagnostics.register(
    plugin = name,
    dataDirectory = dataFolder.toPath(),
    contributor = DiagnosticContainer.builder("auction")
        .snapshot(Supplier {
            mapOf(
                "activeLots" to auction.activeLots,
                "cacheSize" to auction.cacheSize,
                "databaseConnected" to database.isConnected
            )
        })
        .configuration(DiagnosticConfiguration.file("config.yml")
            .exclude("storage.internalPool")
            .secretKeyRegex("(?i).*(password|token|secret|webhook).*")
            .redactValueRegex("license-[A-Za-z0-9-]+")
            .build())
        .configuration("messages.yml")
        .configuration("gui.yml")
        .build()
)
```

`snapshot` должен быстро читать готовое состояние из памяти и не выполнять сетевые или блокирующие запросы.

Состояние компонента:

```kotlin
pn.diagnostics.status(
    plugin = name,
    component = "database",
    state = "CONNECTED",
    detail = "MySQL pool готов",
    fields = mapOf("poolSize" to database.poolSize)
)
```

Ошибка:

```kotlin
pn.diagnostics.record(
    plugin = name,
    level = DiagnosticLevel.ERROR,
    component = "auction",
    code = "LOTS_LOAD_FAILED",
    message = "Не удалось загрузить лоты",
    error = exception,
    fields = mapOf("storage" to database.type)
)
```

При выключении:

```kotlin
override fun onDisable() {
    diagnostics?.close()
    metrics?.close()
}
```

## Команда

```text
/pndebug all
/pndebug pnMarket
/pndebug pnMarket --config
/pndebug all --full
/pndebug all --local
```

Право: `pnlibrary.debug`.

Отчёт содержит сведения о JVM, операционной системе, памяти, потоках, платформе, установленных плагинах и зарегистрированных контейнерах. Конфигурации читаются только из переданной папки плагина. Выход за неё и симлинки блокируются. Секреты удаляются перед шифрованием.

По умолчанию локальный файл имеет расширение `.pndebug`, а загрузка отправляет только зашифрованный конверт. Приватный ключ в серверные JAR не входит.

## Правильный lifecycle

1. Установите pnLibrary и объявите зависимость.
2. Получите общий `PnLibrary` при включении плагина.
3. Создайте MBox, метрики и диагностическую регистрацию.
4. Сохраните `PluginMetrics` и `DiagnosticRegistration`.
5. Закройте их при выключении плагина.

Расширенный пример находится в [INTEGRATION.md](INTEGRATION.md).
