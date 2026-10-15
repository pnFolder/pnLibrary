# Архитектура pnLibrary

## Что использует потребитель

Обычный plugin должен работать только с `PnLibrary` и его сервисами:

```text
Plugin
  |
  v
PnLibrary
  |
  +--> commands.register(owner, ...) команды с явным lifecycle-владельцем
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
library.commands.register(plugin, "hello") { ... }
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

## Почему в SPI несколько platform-интерфейсов

Интерфейсы `PlatformIdentity`, `PlatformExecution`, `PlatformOwnership`,
`PlatformDiagnostics` и другие не являются отдельными реализациями для каждой
платформы. Это узкие capability-контракты, которые позволяют core-компоненту
зависеть только от нужной ему возможности:

```text
UpdateService        -> PlatformIdentity + PlatformExecution
PluginRegistry       -> PlatformOwnership + PlatformExecution
Diagnostics          -> PlatformDiagnostics + PlatformIdentity
CommandService       -> PlatformLogging
```

Такое разделение оставляем, потому что оно уменьшает связанные зависимости внутри
core. Упрощать нужно не механическим слиянием всех интерфейсов, а удалением
контрактов без самостоятельного смысла и вынесением повторяющегося native-кода в
общие generic-адаптеры. Именно поэтому регистрации команд уже используют общий
`GenericPlatformCommandAdapter`, а Bukkit/Bungee/Velocity содержат только свой
native bridge.

## Пример

```kotlin
val registration = library.commands.register(plugin, "hello") {
    permission("example.hello")
    executes { context ->
        context.sender.send(Component.text("Hello!"))
    }
}
```

Этот код не зависит от Bukkit, BungeeCord или Velocity. Для plugin lifecycle
достаточно закрыть возвращённый `CommandRegistration` или закрыть runtime/plugin
owner — библиотека удалит native-регистрацию автоматически.
