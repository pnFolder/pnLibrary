# Архитектура pnLibrary

Потребитель получает команды и задачи только через `ModuleContext`. Native plugin
передаётся один раз при регистрации; модуль закрепляет владельца и срок жизни ресурсов.

```kotlin
val registration = library.plugins.register(plugin)
val context = registration.registerModule("main")

context.registerCommand("hello") {
    executes { it.sender.send(Component.text("Hello!")) }
}

context.tasks.schedule(TaskSpec.builder()
    .name("refresh")
    .interval(Duration.ofMinutes(1))
    .action { refresh() }
    .build())

// При отключении плагина:
registration.close()
```

## Владение

- `PluginRegistration` объединяет модули одного native plugin.
- `ModuleContext` владеет своими командами и отдельным task scope.
- Закрытие модуля удаляет его команды и отменяет задачи, не затрагивая соседние модули.
- Закрытие plugin registration закрывает все его модули.
- Native command manager и scheduler получают владельцем исходный plugin, а не ModuleContext.

У `PnLibrary` нет `commands`, `tasks`, `registerRuntimeCommand` или
`runtimeTaskScope`. Передавать произвольный owner при создании команды/задачи
в публичном API больше нельзя.

## Внутренний путь команды

```text
ModuleContext.registerCommand
    -> internal CommandService
    -> PlatformCommandAdapter
    -> NativeCommandRegistrar
    -> native command manager
```

Задача проходит через `ModuleContext.tasks`, внутренний `TaskService` и
`PlatformTaskAdapter`. Контекст определяет модуль и native owner; платформа
реализует нативное выполнение.

## Границы модулей

| Слой | Назначение |
| --- | --- |
| modules/api | PnLibrary, ModuleContext, task scopes, command definitions и handles |
| modules/core | Внутренние command/task services, ownership и lifecycle |
| modules/runtime-spi | Контракты native регистрации и выполнения |
| platforms/*/api | Публичные платформенные возможности |
| platforms/*/runtime | Нативная интеграция |

Встроенным функциям pnLibrary нужны собственные служебные задачи и команды.
Их core-мост `RuntimeResources` не входит в consumer API. Платформенные модули
используют его для встроенных команд, меню и startup-задач; consumer plugins
должны зависеть от API и получать ModuleContext.

Другие возможности — конфигурации, события, сервисы, плейсхолдеры и метрики —
по-прежнему доступны через соответствующие поля контекста. Этот переход
не меняет их глобальные API.
