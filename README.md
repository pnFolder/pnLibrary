# pnLibrary

Общая библиотека плагинов pnFolder для Bukkit/Paper/Folia, BungeeCord и Velocity: одна установка на сервер,
общий рантайм для всех плагинов студии.

Что даёт плагинам:

- **Обновления.** Каталог релизов, каналы (stable, rc, beta, alpha, dev), проверка SHA-256, подготовка к
  перезапуску, резервные копии и откат, история с автором каждого действия, пауза до 7 дней.
- **Диагностика.** «Чёрный ящик»: ошибки и предупреждения всех плагинов с удалением секретов, зашифрованная
  история, отчёт `/pndebug` для поддержки.
- **Метрики.** bStats и FastStats через один интерфейс, с уважением настройки отключения bStats.
- **Инструменты для плагинов.** Команды, задачи (включая Folia), события, конфигурации, плейсхолдеры,
  кулдауны, сообщения на Adventure, валюта (Vault/PlayerPoints), меню и предметы для Bukkit.

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
| `/pn status` | состояние сервера и плагинов |
| `/pndebug [all\|плагин]` | отчёт для поддержки |

На BungeeCord и Velocity каталог доступен из консоли командой `/pnupdate`.

## Подключение к плагину

```kotlin
dependencies {
    compileOnly("io.github.pnfolder:pnlibrary-api:<версия>")
    // для Bukkit-специфичных возможностей (меню, предметы)
    compileOnly("io.github.pnfolder:pnlibrary-bukkit-api:<версия>")
}
```

Плагин получает `PnLibrary` через `PnLibraryProvider` и никогда не зависит от классов `core`: публичный API
защищён проверкой бинарной совместимости.

## Сборка

```
./gradlew build                          # все модули, тесты, проверка API
./gradlew :distribution:shadowUniversal  # distribution/build/libs/pnLibrary-<версия>-universal.jar
```

`installAcceptanceToPaper` копирует сборку на тестовый сервер; путь задаётся свойством `pnlibrary.installDir`
(например, в `~/.gradle/gradle.properties`).

## Документация

- [Архитектура и модули](docs/architecture.md)
- [Команды: ошибки, уровни ответа, полный пример](docs/commands.md)
- [Система обновлений](docs/updates.md)
- [Валюта](docs/currency.md)
