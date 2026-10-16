# Миграция на регистрацию через ModuleContext

Это намеренное несовместимое изменение публичного API команд и задач. Прямые
owner-based точки входа удалены, без deprecated-обёрток, чтобы они не оставались
вторым способом регистрации.

| Прежний вызов | Новый вызов |
| --- | --- |
| `library.commands.register(plugin, definition)` | `context.registerCommand(definition)` |
| `library.commands.register(plugin, "name") { ... }` | `context.registerCommand("name") { ... }` |
| `library.tasks.scope(plugin)` | `context.tasks` |
| `library.tasks.query()` | `context.tasks.query()` для текущего модуля |
| `library.registerRuntimeCommand(...)` в consumer plugin | `context.registerCommand(...)` |
| `library.runtimeTaskScope()` в consumer plugin | `context.tasks` |

```kotlin
val registration = library.plugins.register(plugin)
val context = registration.registerModule("main")

context.registerCommand("hello") {
    executes { invocation ->
        invocation.sender.send(Component.text("Hello"))
    }
}

context.tasks.schedule(TaskSpec.builder()
    .name("refresh")
    .interval(Duration.ofMinutes(1))
    .action { refresh() }
    .build())

// При выключении native plugin:
registration.close()
```

Для Java:
```java
PluginRegistration registration = library.getPlugins().register(plugin);
ModuleContext context = registration.registerModule("main", builder -> {});
context.registerCommand(commandDefinition);
context.getTasks().schedule(taskSpec);
// При выключении:
registration.close();
```

Модуль автоматически освобождает его команды и задачи. При необходимости можно
досрочно закрыть отдельный CommandRegistration или отменить TaskHandle.
Владелец нативной регистрации — исходный plugin; соседние модули изолированы.

CommandService и TaskService перенесены из API во внутренние пакеты core.
Consumer plugins, собранные со старыми вызовами, необходимо мигрировать и пересобрать.
Внутренние pnLibrary-интеграции используют core RuntimeResources; этот мост
не является заменой ModuleContext для сторонних плагинов.
