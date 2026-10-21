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
modules/core ──────────► modules/runtime-spi     рантайм: обновления, журнал, команды, метрики, отчёты
      ▲                        ▲
      │                        │ реализует
platforms/*/runtime                              адаптеры платформ: команды, задачи, логи, игроки
      ▲
distribution                                     итоговые jar-файлы, в том числе universal
```

Плагин видит только интерфейсы и модели. Где лежит реализация, он не знает, и она может меняться, не ломая его.
Сервисы платформы плагин получает так:

```kotlin
val library = PnLibraryProvider.get()
val bukkit = library.platform<BukkitPlatform>()
bukkit.items.fromSection(config.getConfigurationSection("reward"))
bukkit.menus.open(this, player, menu)
```

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
| `modules/console` | карточки и деревья для консоли (Java, без зависимостей) |
| `modules/features/config-*` | типизированные YAML-конфиги: API и реализация, см. [configuration.md](configuration.md) |
| `modules/features/currency-*` | валюта: API и реализация хранилищ, см. [currency.md](currency.md) |
| `modules/features/minecraft-localization` | переводы ванильных названий (Bukkit) |
| `platforms/bukkit/api` | контракты Bukkit: `BukkitPlatform`, меню, предметы, визуальные сущности, `ServerInfo` |
| `platforms/bukkit/runtime` | адаптер Bukkit, Spigot, Paper и Folia и реализации контрактов выше |
| `platforms/bukkit/paper` | регистрация команд через Brigadier на Paper (Java 21) |
| `modules/bootstrap` | общая часть встраиваемого установщика: версии, релиз GitHub, проверка и установка `universal.jar` |
| `platforms/*/bootstrap` | вход установщика для Bukkit, BungeeCord и Velocity: плагин сам ставит pnLibrary и проверяет версию |
| `platforms/bungee/api`, `platforms/velocity/api` | доступ к прокси-серверу платформы |
| `platforms/bungee/runtime`, `platforms/velocity/runtime` | адаптеры прокси |
| `platforms/velocity/faststats` | мост FastStats для Velocity (Java 21) |
| `distribution` | сборка jar-файлов и офлайн-установщика обновлений |
| `examples/acceptance-bukkit` | приёмочный плагин на Java |
| `tools/*` | скрипты релиза, проверки поколения API и приёмки |

## Пакеты `core`

| Пакет | За что отвечает |
|---|---|
| `core.runtime` | запуск рантайма (`PnLibraryLauncher`), жизненный цикл, `config.yml`, часовой пояс |
| `core.commands` | дерево команд, разбор аргументов, пять уровней ответа на ошибки |
| `core.journal` | журнал событий `/pnevents` и сводка ошибок при входе |
| `core.observability`, `core.diagnostics`, `core.logging` | «чёрный ящик», отчёты для поддержки, логи |
| `core.upload` | загрузка зашифрованных отчётов |
| `core.updates.catalog` | страницы и команды каталога обновлений |
| `core.updates.service` | сервис обновлений и оркестратор |
| `core.updates.policy` | `updates.yml`, каналы, автозагрузка, переопределения |
| `core.updates.install` | подбор версий, подготовка, установка, офлайн-установщик |
| `core.updates.history` | журнал обновлений, состояние, снимки конфигов |
| `core.updates.engine` | каталоги релизов, загрузка, проверка, транзакции, пауза |
| `core.downloads` | загрузка зависимостей плагинов |
| `core.metrics` | bStats и FastStats за одним `MetricsService` |
| `core.plugin` | регистрация плагинов и модулей |
| `core.remote` | политики |
| `core.placeholders`, `core.events`, `core.tasks`, `core.cooldowns`, `core.services`, `core.audiences`, `core.text` | реализации одноимённых сервисов API |
| `core.ui` | цвета палитры для консоли |
| `core.io`, `core.security`, `core.lifecycle` | текстовые файлы, шифрование, очистка ресурсов |

## Сообщения

Все сообщения строятся через `api.chat.ChatKit`:

- в начале пустая строка и заголовок;
- секции отделяются пустой строкой, без рамок и линий;
- подробности спрятаны в карточки при наведении;
- у кнопок серые скобки и цветная подпись.

Цвета берутся из ролей `ChatPalette`. На серверах до 1.16 каждая роль превращается в свой стандартный цвет.
Консоль получает тот же текст без кнопок, в ASCII и с ближайшими стандартными цветами.
