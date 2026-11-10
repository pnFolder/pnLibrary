# Архитектура

pnLibrary запускается на сервере один раз и раздаёт всем плагинам pnFolder общие сервисы через публичный API.
Всё, что зависит от платформы (Bukkit, BungeeCord, Velocity), спрятано в адаптерах.

## Слои

```
плагины pnFolder
      │  compileOnly: видят только контракты
      ▼
modules/api, modules/common, features/*-api      публичный контракт, проверяется на бинарную совместимость
platforms/*/api                                  контракты конкретной платформы (меню, предметы, ...)
      ▲
      │ реализует
modules/core + внутренние модули ► modules/runtime-spi
                                                 рантайм: команды, метрики, отчёты, сборка частей;
                                                 link, placeholders, events, upload, journal, updates вынесены в свои модули
      ▲                        ▲
      │                        │ реализует
platforms/*/runtime                              адаптеры платформ: команды, задачи, логи, игроки
      ▲
distribution                                     итоговые jar-файлы: bukkit, bungeecord, velocity, universal, universal-full
```

Плагин видит только интерфейсы и модели. Где лежит реализация, он не знает, и она может меняться, не ломая его.
Сервисы платформы плагин получает так:

```kotlin
val library = PnLibraryProvider.get()
val bukkit = library.platform<BukkitPlatform>()
bukkit.items.fromSection(config.getConfigurationSection("reward"))
bukkit.menus.open(this, player, menu)
```

## Создание сервисов и владение ресурсами

Конкретные реализации собираются в `RuntimeFeatureSet`, `PlatformServiceSet` и `PnLibraryImpl`.
`PluginRegistryImpl` получает готовые зависимости через конструктор; он не создаёт резервную реализацию
конфигурационного сервиса. Создание ресурсов логического модуля сосредоточено в `ModuleResourceFactory`.

Интерфейсы в фабрике ресурсов стоят только на границах модулей Gradle:

| Контракт | Что получает модуль |
|---|---|
| `ConfigurationRuntime` | конфигурационный scope по владельцу и id модуля |
| `CurrencyRuntime` | собственное представление валюты и общую фабрику хранилищ |
| `ServiceScope` | реестр с регистрациями, которые заканчиваются вместе с модулем |
| `ExpansionManager` | доступ к общим расширениям через существующий API-контракт |

`ConfigurationRuntime` и `CurrencyRuntime` лежат в реализационных feature-модулях: это граница их интеграции
с `core`. Плагины используют контракты из `*-api`. Внутри `core` у одной реализации нет отдельного интерфейса:
`PlaceholderHub`, `DirectDownloads`, `ServiceRegistry` и `RemotePolicyMonitor` используются как есть,
а тесты подменяют их через Mockito.

Общие сервисы закрывает рантайм, а полученные от них scope и регистрации — модуль. Если создание модуля
прерывается, фабрика закрывает уже полученные ресурсы и сам `ServiceScope`. Ошибка очистки не мешает
освобождению остальных ресурсов и прикладывается к первоначальной ошибке.

Модели, билдеры, кеши и небольшие вычислительные помощники остаются конкретными типами. Интерфейс выделяется
для границы между подсистемами, где потребителю нужен контракт поведения и управления ресурсами.

## Что проверяет сборка

| Правило | Где проверяется |
|---|---|
| публичные модули не зависят от реализаций | задача `verifyPublicModuleBoundaries` |
| в публичных модулях нет рефлексии и платформенной реализации | `SourceConventionsTest` |
| `core` не знает о платформах, платформы не знают друг о друге | `SourceConventionsTest` |
| каждое изменение публичного API видно в слепках `*/api/*.api` | `apiCheck`, входит в `check` |
| импорты без `*`, цвета только из палитры, у fluent-методов явный тип | `SourceConventionsTest` |

Байткод по умолчанию Java 8. Модуль повышает уровень свойством `pn.java.release` в своём `gradle.properties`:
Velocity требует 17, Paper Brigadier и FastStats для Velocity требуют 21.

## Модули

| Модуль | Назначение |
|---|---|
| `modules/common` | общие помощники JVM: `AtomicFiles`, `Digests`, `RussianText`, версии Minecraft и Java |
| `modules/api` | публичный API: команды, задачи, события, конфиги, обновления, диагностика, метрики, чат |
| `modules/runtime-spi` | контракт адаптера платформы и общие части адаптеров |
| `modules/core` | реализация рантайма |
| `modules/updates` | обновления: каталог релизов, выбор версий, установка, откат; от `core` не зависит, `core` подключает её и передаёт тему консоли |
| `modules/link`, `placeholders`, `events`, `upload`, `journal` | внутренние модули, вынесенные из `core`: связь серверов, плейсхолдеры, шина событий, загрузка отчётов, журнал `/pn events`. Пакеты сохранили имена `org.pnfolder.pnlibrary.core.<имя>`; в `apiCheck` не входят |
| `modules/console` | карточки и деревья для консоли (Java, без зависимостей) |
| `modules/features/config-*` | типизированные YAML-конфиги: API и реализация, см. [configuration.md](configuration.md) |
| `modules/features/currency-*` | валюта: API и реализация хранилищ, см. [currency.md](currency.md) |
| `modules/features/minecraft-localization-api` | платформонезависимый API загрузки и поиска ванильных переводов Mojang (без Bukkit) |
| `platforms/bukkit/api` | контракты Bukkit: `BukkitPlatform`, меню, предметы, визуальные сущности, `ServerInfo` |
| `platforms/bukkit/runtime` | адаптер Bukkit, Spigot, Paper и Folia и реализации контрактов выше |
| `platforms/bukkit/paper` | регистрация команд через Brigadier на Paper (Java 21) |
| `platforms/bukkit/localization` | реализация `minecraft-localization-api` для Bukkit: перевод и поиск материалов, зачарований, существ и т.д. (`BukkitLocaleTranslations`); входит в Bukkit- и universal-JAR, плагинам подключается как `compileOnly` |
| `platforms/loader` | Java 8 загрузчик: при старте скачивает Kotlin и Adventure (Libby) для лёгкого `universal`, при `universal-full` ничего не качает |
| `modules/bootstrap` | общая часть встраиваемого установщика: версии, релиз GitHub, проверка и установка `universal.jar` |
| `platforms/*/bootstrap` | вход установщика для Bukkit, BungeeCord и Velocity: плагин сам ставит pnLibrary и проверяет версию |
| `platforms/bungee/api`, `platforms/velocity/api` | доступ к прокси-серверу платформы |
| `platforms/bungee/runtime`, `platforms/velocity/runtime` | адаптеры прокси |
| `platforms/velocity/faststats` | мост FastStats для Velocity (Java 21) |
| `distribution` | сборка jar-файлов и офлайн-установщика обновлений |
| `integration` | инструкция для авторов плагинов и ИИ, заготовка папки `.pnlibrary`, см. [integration/README.md](../integration/README.md) |
| `tools/release`, `tools/api`, `tools/ci` | скрипты, которые запускает CI: выпуск релиза, проверка поколения API, подготовка нативных сборок |
| `tools/support` | `decode_pnsupport.py`: расшифровка отчётов `/pn debug` |

## Пакеты `core`

| Пакет | За что отвечает |
|---|---|
| `core.runtime` | запуск рантайма (`PnLibraryLauncher`, корень сборки `PnLibraryImpl`), жизненный цикл, `config.yml`, часовой пояс |
| `core.commands` | дерево команд, разбор аргументов, пять уровней ответа на ошибки |
| `core.commands.events`, `.tasks`, `.groups`, `.proxy` | команды `/pn bus`, `/pn tasks`, группы серверов, `/pn servers` и управление прокси |
| `core.menu` | меню `/pn gui`, платформенно-нейтральное; данные берёт у `MenuData` (свои или чужого сервера по связи) |
| `core.link` (модуль `link`) | связь серверов через плагин-сообщения: подпись, доклады, запросы данных, удалённые действия |
| `core.stats` | счётчики сервера для `/pn stats` и меню |
| `core.journal` (модуль `journal`) | журнал событий `/pn events` и сводка ошибок при входе |
| `core.observability`, `core.diagnostics`, `core.logging` | «чёрный ящик», отчёты для поддержки, логи |
| `core.upload` (модуль `upload`) | загрузка зашифрованных отчётов |
| `core.updates.*` (модуль `updates`) | `catalog` команды и страницы, `service` сервис обновлений, `policy` каналы и автозагрузка, `install` подбор версий и установка, `history` журнал и снимки, `engine` каталоги, загрузка, проверка, транзакции, `databases` копии баз данных |
| `core.downloads` | загрузка зависимостей плагинов |
| `core.metrics` | bStats и FastStats за одним `MetricsService` |
| `core.plugin` | регистрация плагинов и модулей |
| `core.plugin.module` | сборка модуля: ресурсы, метаданные, зависимости, расширения |
| `core.remote` | политики |
| `core.placeholders`, `core.events` (свои модули), `core.tasks`, `core.cooldowns`, `core.services`, `core.audiences`, `core.text`, `core.expansions` | реализации одноимённых сервисов API |
| `core.ui` | цвета палитры для консоли |
| `core.security`, `core.cleanup`, `core.lifecycle` | шифрование, очистка ресурсов, связь рантайма с ресурсами |
| `core.proxy` | сборка связи с прокси (`ProxyLink`) |

## Сообщения

Все сообщения строятся через `api.chat.ChatKit`:

- в начале пустая строка и заголовок;
- секции отделяются пустой строкой, без рамок и линий;
- подробности спрятаны в карточки при наведении;
- у кнопок серые скобки и цветная подпись.

Цвета берутся из ролей `ChatPalette`. На серверах до 1.16 каждая роль превращается в свой стандартный цвет.
Консоль получает тот же текст без кнопок, в ASCII и с ближайшими стандартными цветами.
