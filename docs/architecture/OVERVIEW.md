# Архитектура pnLibrary

## Что использует потребитель

Обычный plugin должен работать только с `PnLibrary` и его сервисами:

```text
Plugin
  |
  v
PnLibrary
  |
  +--> registerCommand(...)      команды
  +--> audiences                 сообщения и получатели
  +--> tasks                     задачи и TaskScope
  +--> plugins                   lifecycle модулей
  +--> metrics / diagnostics     наблюдаемость
```

Потребителю не нужно создавать `PlatformAdapter`, `PlatformCommandDispatcher` или
`NativeCommandRegistrar`. Эти типы относятся к runtime-интеграции платформ.

## Что происходит внутри

```text
PnLibrary API
    |
    v
Core services
    |
    v
Platform bridge
    |
    +--> Bukkit API
    +--> BungeeCord API
    +--> Velocity API
```

`api` содержит стабильные контракты. `core` содержит общую логику и lifecycle.
Platform runtime переводит вызовы общего ядра в native API конкретного сервера.

## Поток регистрации команды

```text
library.registerCommand("hello") { ... }
    |
    v
CommandService
    |
    v
CommandServiceImpl
    |
    v
PlatformCommandAdapter
    |
    v
NativeCommandRegistrar
    |
    v
Bukkit / Bungee / Velocity command API
```

Обработчик команды и дерево аргументов создаются один раз в общем API. Платформа
передаёт в core только native sender и аргументы, после чего core формирует
портативный `CommandContext` и выполняет одинаковую логику на всех платформах.

## Границы пакетов

| Слой | Назначение | Кому нужен |
|---|---|---|
| `modules/api` | Публичные сервисы и модели | Consumer plugins |
| `modules/core` | Общая реализация сервисов | Runtime composition |
| `modules/runtime-spi` | Контракты между core и платформами | Platform adapters |
| `platforms/*/api` | Native platform-facing API | Platform integrations |
| `platforms/*/runtime` | Bukkit/Bungee/Velocity bridge | Runtime only |

Правило для новых возможностей: сначала определить пользовательский контракт в
`api`, затем реализовать общую логику в `core`, и только после этого добавить
минимальный native bridge в platform runtime.

## Пример

```kotlin
val registration = library.registerCommand("hello") {
    permission("example.hello")
    executes { context ->
        context.sender.send(Component.text("Hello!"))
    }
}
```

Этот код не зависит от Bukkit, BungeeCord или Velocity. Для plugin lifecycle
достаточно закрыть возвращённый `CommandRegistration` или закрыть runtime/plugin
owner — библиотека удалит native-регистрацию автоматически.
