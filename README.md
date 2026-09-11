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
| `pnlibrary-api` | Публичные интерфейсы: runtime, сервисы, события, задачи и остальные общие возможности | 8 |
| `pnlibrary-bukkit-api` | Публичные Bukkit-контракты: окружение сервера и меню | 8 |
| `pnlibrary-runtime-spi` | Внутренняя граница между core и платформами | 8 |
| `pnlibrary-core` | Закрытое исполнение общих сервисов | 8 |
| `pnlibrary-bstats-base` | Неизменённые общие классы официального bStats | 8 |
| `pnlibrary-bukkit` | Bukkit, Spigot, Paper, Purpur, Leaf и Folia | 8 |
| `pnlibrary-bungee` | BungeeCord и совместимые прокси | 8 |
| `pnlibrary-velocity` | Velocity | 17 |
| `pnlibrary-distribution` | Сборка готовых платформенных JAR | — |

Краткая карта кода для разработчика: [HELP-README.md](HELP-README.md).

Typed configuration actions, plugin visibility, namespaces and lifecycle:
[docs/ACTIONS.md](docs/ACTIONS.md). Release preparation rules:
[RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md).

Проект собирается на JDK 17 или новее. Bukkit, включая Folia-ветку планировщика,
и BungeeCord получают байткод Java 8; Velocity — Java 17. Жёсткой привязки к
конкретной установленной JDK нет.

## Установка

Сборка:

```text
gradlew.bat clean test :pnlibrary-distribution:build
```

Готовые файлы находятся в `pnlibrary-distribution/build/libs`:

- `pnLibrary-2.1.0-beta.1-bukkit-java8.jar`;
- `pnLibrary-2.1.0-beta.1-bungeecord-java8.jar`;
- `pnLibrary-2.1.0-beta.1-velocity-java17.jar`;
- `pnLibrary-api-2.1.0-beta.1.jar` и sources для разработчиков;
- `pnLibrary-bukkit-api-2.1.0-beta.1.jar` и sources для Bukkit-разработчиков.

Положите один подходящий JAR в папку `plugins` и полностью перезапустите сервер.

При первом запуске создаётся `plugins/pnLibrary/config.yml`. В нём настраиваются
загрузка отчётов, режим шифрования, сбор конфигураций и журналов, cooldown и сроки
хранения. Неизвестные параметры и некорректные значения останавливают запуск с
понятной ошибкой, чтобы опечатка не меняла политику приватности незаметно.
Полная таблица находится в [docs/RUNTIME_CONFIGURATION_RU.md](docs/RUNTIME_CONFIGURATION_RU.md).

## Подключение API

Сначала можно опубликовать API в локальный Maven-кэш:

```text
gradlew.bat :pnlibrary-api:publishToMavenLocal :pnlibrary-bukkit-api:publishToMavenLocal
```

В плагине:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    compileOnly("ru.privatenull:pnlibrary-api:2.1.0-beta.1")
}
```

Для Bukkit-меню подключается отдельный публичный артефакт:

```kotlin
compileOnly("ru.privatenull:pnlibrary-bukkit-api:2.1.0-beta.1")
```

Для Bukkit достаточно объявить `pnlibrary-bukkit-api`: общий API подтянется как
транзитивная зависимость. `pnlibrary-core`, `pnlibrary-runtime-spi` и платформенные runtime
модули не подключаются к пользовательскому плагину. API нельзя встраивать через
`implementation`, Shadow или relocation: его предоставляет установленная pnLibrary.

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

Bukkit, BungeeCord и Velocity:

```kotlin
val pn = PnLibraryProvider.get()
```

Для необязательной интеграции доступен `PnLibraryProvider.getOrNull()`.

## Собственный ServiceManager

`pnLibrary` не использует сервис-менеджеры Bukkit, BungeeCord или Velocity.
Общий реестр одинаково работает на любой платформе. Контракт сервиса находится
в API плагина, а реализация остаётся внутри самого плагина:

```kotlin
interface EconomyService {
    fun balance(userId: UUID): BigDecimal
}

// Публикация от имени уже зарегистрированного плагина.
context.services.register<EconomyService>(economyService, priority = 100)

// Получение из любого плагина в этом процессе.
val economy = pn.services.require<EconomyService>()
val optionalEconomy = pn.services.get<EconomyService>()
```

Приоритет — любое `Int`; выбирается провайдер с наибольшим значением. Один
`PluginId` не может дважды опубликовать один контракт, поэтому случайная
дубликация обнаруживается сразу. Разные плагины могут предложить реализации
одного контракта. `context.services.unregister<EconomyService>()` удаляет одну
реализацию, а `context.close()` автоматически удаляет все сервисы владельца. Создавать scope
или вручную передавать `PluginId` для регистрации не требуется.

## Глобальная регистрация плагина

Плагин один раз регистрируется в pnLibrary. Адаптер сам получает его стабильный
ID, имя, версию и авторов из Bukkit, BungeeCord или Velocity. Все подключённые
возможности доступны из одного `PluginContext`:

```kotlin
private lateinit var context: PluginContext

override fun onEnable() {
    val pn = PnLibraryProvider.get()
    context = pn.plugins.register(this) {
        it.metrics(projectId = 12345, enabled = true) { metrics ->
            metrics.simplePie("storage_type") { database.type }
        }
        it.listener(MarketListener())
        it.diagnostics(dataFolder.toPath(), diagnosticContainer)
        it.updates("pnFolder", "pnMarket") { updates ->
            updates.channel(UpdateChannel.STABLE)
                .automaticDownload(true)
                .artifact("(?i)^pnMarket-.*\\.jar$", minimumJava = 17)
        }

    }

    context.lifecycle.enabled()
        .ok("Configuration", "loaded")
        .ok("Database", "${database.type}, lots: ${auction.activeLots}")
        .show()
}

override fun onDisable() {
    val savedLots = auction.saveAll()
    context.close()
    context.lifecycle.disabled()
        .ok("Storage", "saved lots: $savedLots")
        .show()
}
```

Контекст можно получить из любого места по идентификатору:

```kotlin
val context = pn.plugins.require("pnmarket")
context.logger.success("Market loaded")
context.events.publish(MarketReloadEvent())
context.tasks.async(Runnable { repository.cleanup() })

context.metadata.version
context.metadata.javaFeature
context.metadata.platformImplementation
context.updates?.snapshot
```

`enabled()` и `disabled()` создают буферизованный MBox: вызовы `ok`, `warn`,
`skip` и `fail` ничего не печатают. Весь блок выводится одним вызовом `show()`.
Библиотека заранее добавляет ID, версию, платформу, Java, metrics, updater,
diagnostics и listeners; плагин дописывает только свои строки и сам выбирает
правильный момент показа.

Явный ID остаётся override для нестандартных случаев:

```kotlin
pn.plugins.register(this, "custom-id") { plugin ->
    plugin.metadata { metadata ->
        metadata.name("pnMarket")
            .version("1.5.0")
            .authors("pnFolder")
    }
}
```

Native owner повторно передавать не требуется:

```kotlin
context.lifecycle.enabled()
    .ok("Configuration", "7 files loaded")
    .ok("Database", "connected")
    .show()

context.lifecycle.disabled()
    .ok("Storage", "saved: $savedCount")
    .show()
context.close()
```

Метрики управляются во время работы без повторной регистрации плагина:

```kotlin
context.metrics.disable()
context.metrics.enable()
context.metrics.changeProjectId(54321)
```

При смене ID активная bStats-сессия безопасно перезапускается, а настроенные
диаграммы применяются повторно. Нативный объект плагина используется внутри
только для платформенных операций; события принадлежат стабильному `PluginId`.

## Логирование и MBox

```kotlin
val log = pn.logging.logger(this, "pnMarket")

log.info("Загрузка аукциона")
log.success("Аукцион загружен")
log.warning("Vault не найден")
log.error("Не удалось подключиться к базе", exception)
```

Произвольный красивый блок, привязанный к уже зарегистрированному плагину:

```kotlin
val message = context.messages.box("СИНХРОНИЗАЦИЯ АУКЦИОНА")
    .ok("Конфигурация", "загружена")
    .ok("Команды", "зарегистрированы")
    .skip("PlaceholderAPI", "не установлен")
    .fail("Database", "подключение не установлено", exception)

// До этой строки ничего не напечатано.
message.show()
```

`context.messages.box(...)` не является сообщением включения или выключения.
Это нейтральный MBox для загрузки данных, миграций, отчётов, проверок и любых
других операций. Каждый созданный MBox можно показать ровно один раз.

MBox работает на всех платформах:

- Bukkit/Paper/Leaf/Folia — через logger Bukkit-плагина;
- BungeeCord — через logger Bungee-плагина;
- Velocity — через штатный SLF4J logger.

Повторяющиеся `WARNING` и `ERROR` агрегируются за текущую сессию. Первый случай
печатается в консоль полностью; затем одинаковый stack trace подавляется, а на
контрольных количествах и не чаще одного раза в пять минут выводится краткая
сводка. В `/pndebug --logs` сохраняются полный исходный блок ошибки со всей
цепочкой `Caused by`/`Suppressed`, место возникновения, общее число повторений и
точное время с именем потока для каждого сохранённого повторения.

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
Velocity. Событие реализует `Event`, а подписки хранятся в scope владельца:

```kotlin
data class ClanCreatedEvent(val clanId: String) : Event()

private val events = pn.plugins.require("pnclans").events

events.subscribe<ClanCreatedEvent> { event ->
    logger.info("Создан клан ${event.clanId}")
}

events.subscribe<ClanCreatedEvent>(priority = 250) { event ->
    audit.save(event)
}

ClanCreatedEvent("knights").callEvent().thenAccept { allowed ->
    if (!allowed) logger.warn("Создание клана отменено")
}
```

`event.callEvent()` возвращает `CompletableFuture<Boolean>`, потому что обработчики
могут выполняться позже в другом execution context. Future содержит `false`, если
событие реализует `Cancellable` и было отменено. Подробный `EventDispatchResult`
доступен через `events.publish(event)`. Имя события находится в `event.eventName`
и по умолчанию равно имени класса.

Режим события задаётся явно и действительно выбирает execution context:

```kotlin
data class ClanCacheLoadedEvent(val clans: Int) : Event(EventMode.ASYNC)

ClanCacheLoadedEvent(loadedClans).callEvent()
```

`EventMode.SYNC` отправляет listeners в main/global scheduler платформы,
`EventMode.ASYNC` — в фоновый executor библиотеки. Метод вызова один для обоих
режимов. Async-listener не должен обращаться к API, которому требуется platform
thread. Не блокируйте серверный поток через `join()`; используйте `thenAccept`.

Для привычного Bukkit/Bungee/Velocity-подобного стиля можно зарегистрировать
класс с аннотированными методами:

```kotlin
class ClanListener : Listener {
    @EventHandler(priority = 250, ignoreCancelled = true)
    fun onClanCreated(event: ClanCreatedEvent) {
        audit.save(event)
    }
}

val registration = events.register(ClanListener())
```

Метод обработчика принимает ровно один `Event` и возвращает `Unit`. Некорректная
сигнатура отклоняется сразу при регистрации. Закрытие `registration` снимает все
методы этого listener’а; закрытие `events` снимает вообще все подписки владельца.

Обычная обработка отправляется в main/global context платформы. Меньший числовой
приоритет запускается раньше: доступны готовые значения `LOWEST = -1000`,
`NORMAL = 0`, `HIGH = 500`, но можно передать любое целое число. При одинаковом
значении сохраняется порядок регистрации. Отменяемое событие дополнительно
реализует `Cancellable`; изоляция ошибок и обработка отмены одинаковы на всех
платформах.
`HandlerList` событиям не нужен: подписки централизованно и потокобезопасно
хранятся в `EventServiceImpl`, а закрываются через plugin scope.
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

## Сервер и версия Minecraft

Версии относятся только к игровому Bukkit-серверу, поэтому находятся в
`pnlibrary-bukkit-api`, а не в общем API. BungeeCord и Velocity могут обслуживать
backend-серверы разных версий и единственной Minecraft-версии не имеют.

```kotlin
val server = PnBukkit.server()
val version = server.minecraftVersion

if (version.isAtLeast(MinecraftVersion.V1_20_5)) enableDataComponents()
if (version.isBetween(MinecraftVersion.V1_8_8, MinecraftVersion.V1_12_2)) {
    enableLegacyInventoryAdapter()
}

logger.info("Ядро: ${server.name} ${server.version}")
logger.info("Minecraft: ${version.text}; raw=${server.rawMinecraftVersion}")
```

Можно проверять версию через готовые методы:

```kotlin
val server = PnBukkit.server()
if (server.isMinecraftAtLeast(MinecraftVersion.V1_20_5)) enableDataComponents()
```

`server.version` содержит версию ядра, например сборку Paper, а
`server.minecraftVersion` — сравнимую версию Minecraft. `ServerCapabilities` и
нативный разбор остаются внутри Bukkit runtime.

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

Локальный отчёт имеет собственный бинарный формат **PN Support Archive** и расширение `.pnsupport`. Обычный текстовый редактор его не открывает: внутри находится зашифрованный диагностический ZIP. В зашифрованный отчёт зарегистрированные конфигурации входят байт-в-байт без переформатирования; незашифрованный локальный ZIP использует очищенные копии. Приватный ключ в серверные JAR не входит.

## Правильный lifecycle

1. Установите pnLibrary и объявите зависимость.
2. Получите общий `PnLibrary` при включении плагина.
3. Создайте MBox, метрики и диагностическую регистрацию.
4. Сохраните `PluginMetrics` и `DiagnosticRegistration`.
5. Закройте их при выключении плагина.

Расширенный пример находится в [INTEGRATION.md](INTEGRATION.md).
