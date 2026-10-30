# pnLibrary

Общая библиотека плагинов pnFolder для Bukkit/Paper/Folia, BungeeCord и Velocity: одна установка на сервер,
общий рантайм для всех плагинов студии.

Что даёт плагинам:

- **Обновления.** Каталог релизов, каналы (stable, rc, beta, alpha, dev), проверка SHA-256, подготовка к
  перезапуску, резервные копии и откат, история с автором каждого действия, пауза до 7 дней.
- **Диагностика.** Журнал событий `/pn events` со стеком вызовов и сводкой ошибок при входе администратора,
  «чёрный ящик» с удалением секретов, зашифрованный отчёт `/pn debug` для поддержки.
- **Метрики.** bStats и FastStats через один интерфейс, с уважением настройки отключения bStats.
- **Инструменты для плагинов.** Команды без `plugin.yml` (на Paper ещё и через Brigadier), задачи (включая Folia),
  события, конфигурации, плейсхолдеры, кулдауны, сообщения на Adventure, валюта (Vault/PlayerPoints), меню и
  предметы для Bukkit.

Поддерживаются серверы с 1.8 и Java 8; Velocity требует Java 17.

## Установка на сервер

Положите `pnLibrary-<версия>-universal.jar` в `plugins/` — один файл работает на Bukkit, BungeeCord и Velocity.

Команды (Bukkit):

| Команда | Что делает |
|---|---|
| `/pn` или `/pn updates` | каталог плагинов и обновлений |
| `/pn updates info <плагин>` | карточка плагина |
| `/pn updates settings <плагин>` | канал, автозагрузка, пауза |
| `/pn updates history` | история обновлений |
| `/pn updates backups` | резервные наборы для отката |
| `/pn check`, `/pn update <плагин>` | проверить, подготовить обновление |
| `/pn gui` | меню: плагины, обновления, история, откаты |
| `/pn status` | состояние сервера и плагинов |
| `/pn events` | журнал событий: ошибки, предупреждения, повторы, стек вызовов |
| `/pninfo` | версия pnLibrary и платформа, для обращения в поддержку |
| `/pn money` | управление валютами плагинов |
| `/pn debug [all\|плагин]` | отчёт для поддержки |

На BungeeCord и Velocity каталог доступен из консоли командой `/pn update`. Все команды обновлений и права описаны
в [docs/updates.md](docs/updates.md).

Остальные права (по умолчанию у операторов):

| Право | Что разрешает |
|---|---|
| `pnlibrary.admin` | всё управление pnLibrary |
| `pnlibrary.events.view` | `/pn events` и сводку ошибок при входе |
| `pnlibrary.debug` | `/pn debug` |

## Подключение к плагину

```kotlin
dependencies {
    compileOnly("io.github.pnfolder:pnlibrary-api:<версия>")
    // для Bukkit-специфичных возможностей (меню, предметы, визуальные сущности)
    compileOnly("io.github.pnfolder:pnlibrary-bukkit-api:<версия>")
}
```

В `plugin.yml` плагина: `depend: [pnLibrary]`.

```kotlin
val library = PnLibraryProvider.get()
val plugin = library.plugins.register(this)
val bukkit = library.platform<BukkitPlatform>()   // меню, предметы, визуальные сущности
```

API состоит из интерфейсов, моделей и небольших служебных помощников (билдеры, ключи, сериализаторы); реализация живёт в рантайме. Плагин никогда не зависит от классов
`core`, а публичный API защищён проверкой бинарной совместимости.

## Сборка

```
./gradlew build                          # все модули, тесты, проверка API
./gradlew :distribution:shadowUniversal  # distribution/build/libs/pnLibrary-<версия>-universal.jar
```

`installAcceptanceToPaper` копирует сборку на тестовый сервер; путь задаётся свойством `pnlibrary.installDir`
(например, в `~/.gradle/gradle.properties`).

## Документация

- [Обзор: весь функционал для людей и ИИ](docs/overview.md)
- [Архитектура и модули](docs/architecture.md)
- [Команды: ошибки, уровни ответа, полный пример](docs/commands.md)
- [События: слушатели, приоритеты, результаты](docs/events.md)
- [Конфигурации](docs/configuration.md)
- [Обновления: команды, права, откат](docs/updates.md)
- [Сеть: прокси, ключ связи, группы серверов](docs/network.md)
- [Подключение плагина к pnLibrary: для людей и ИИ](integration/README.md)
- [Папка `.pnlibrary` в репозитории плагина](docs/repository.md)
- [Действия](docs/actions.md)
- [Проверки](docs/checks.md)
- [Каталог релизов](docs/releases.md)
- [Плейсхолдеры](docs/placeholders.md)
- [Задачи](docs/tasks.md)
- [Диагностика](docs/diagnostics.md)
- [Метрики](docs/metrics.md)
- [Валюта](docs/currency.md)
- [Локализация Minecraft](docs/localization.md)
- [Расширения](docs/expansions.md)
- [Удалённые политики](docs/remote-policies.md)
- [Встраиваемый установщик для всех платформ](modules/bootstrap/README.md)
- [Консольные карточки](modules/console/README.md)
- [Приёмочный плагин](examples/acceptance-bukkit/README.md)
