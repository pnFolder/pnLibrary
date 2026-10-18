# Архитектура

pnLibrary — один рантайм на сервер, который общие для всех плагинов pnFolder сервисы предоставляет через
стабильный публичный API. Платформенные различия изолированы в адаптерах.

## Слои

```
плагины pnFolder
      │  compileOnly
      ▼
modules/api ─────────── modules/common            публичный контракт (бинарная совместимость)
platforms/*/api                                    платформенные расширения API
      ▲
      │ реализует
modules/core ─────────► modules/runtime-spi       рантайм: обновления, диагностика, метрики, UI
      ▲                       ▲
      │                       │ реализует
platforms/bukkit|bungee|velocity/runtime           адаптеры: команды, задачи, логи, аудитории
      ▲
distribution                                       shadow-сборки, в том числе universal
```

Правила, которые проверяет сборка:

- публичные модули (`api`, `common`, `currency-api`, `platforms/*/api`) не зависят от реализаций —
  задача `verifyPublicModuleBoundaries`;
- изменения публичного API видны в дампах `*/api/*.api`, `apiCheck` входит в `check`;
- байткод Java 8 по умолчанию, модуль повышает уровень свойством `pn.java.release` в своём `gradle.properties`.

## Модули

| Модуль | Назначение |
|---|---|
| `modules/common` | общие JVM-помощники: `AtomicFiles`, `Digests`, `RussianText`, версии Minecraft и Java |
| `modules/api` | публичный API: команды, задачи, события, конфиги, обновления, диагностика, метрики |
| `modules/runtime-spi` | контракт платформенного адаптера и общие части адаптеров (`PlatformPlugins`, `JulLogObserver`, `PlatformLifecycle`, `DiagnosticSections`) |
| `modules/core` | реализация рантайма |
| `modules/features/currency-*` | валюта: API и реализация хранилищ (файл, JDBC) |
| `modules/features/minecraft-localization` | переводы ванильных названий (Bukkit) |
| `modules/design/console` | консольные карточки |
| `modules/bootstrap/bukkit` | встраиваемый установщик pnLibrary для плагинов |
| `modules/internal/faststats-velocity` | мост FastStats для Velocity (Java 21) |
| `platforms/*` | API и рантайм-адаптеры Bukkit, BungeeCord, Velocity |
| `distribution` | сборка jar-файлов и офлайн-установщика обновлений |
| `examples/*` | демонстрационные, приёмочные и smoke-плагины |
| `tools/*` | генерация метаданных компонентов, скрипты релиза и проверки |

## Пакеты `core`

| Пакет | Ответственность |
|---|---|
| `core.runtime` | сборка рантайма, жизненный цикл, конфигурация |
| `core.ui` | единый стиль сообщений в чате: `ChatPalette`, `ChatKit`, `ChatNotice` |
| `core.updates.catalog` | страницы и команды каталога обновлений |
| `core.updates.service` | сервис обновлений и оркестратор |
| `core.updates.policy` | `updates.yml`, переопределения, каналы, автозагрузка |
| `core.updates.install` | разрешение графа, подготовка, установка, офлайн-установщик |
| `core.updates.history` | журнал событий, состояние, снимки конфигураций |
| `core.updates.engine` | каталоги релизов, загрузка, проверка, транзакции, пауза |
| `core.observability`, `core.diagnostics`, `core.logging` | чёрный ящик, отчёты, логи |
| `core.metrics` | bStats и FastStats за одним `MetricsService` |
| `core.io` | текстовые файлы с совместимостью с Java 8 |

## Сообщения

Все сообщения игрокам строятся через `core.ui.ChatKit`: заголовок жирным акцентом, голубые подписи, белые
значения, серые скобки у цветных кнопок, секции через пустую строку. Цвета `ChatPalette` подобраны так, что на
серверах до 1.16 каждая роль превращается в свой стандартный цвет. Консоль получает тот же текст в ASCII.
