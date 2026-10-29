# Плейсхолдеры

Плейсхолдер — именованное значение, которое плагин отдаёт другим плагинам и текстам (`%плагин_ключ%`). Сервис доступен как `ModuleContext.placeholders` (`PlaceholderService`).

## Регистрация

Ключ типизирован: `PlaceholderKey.of("balance", Long::class.java)`. Значение настраивается в билдере (`PlaceholderBuilder`): откуда брать значение, кто может его читать (`PlaceholderAccess`), как кэшировать (`PlaceholderCachePolicy`, `PlaceholderCacheScope`).

```kotlin
val registration = context.placeholders.register("balance", Long::class.java) { builder ->
    // настройка резолвера, доступа и кэша
}
// registration.close() снимает плейсхолдер
```

Закрытие `PlaceholderService` снимает всё, что зарегистрировано через него.

## Разбор значений

| Метод | Что делает |
|---|---|
| `resolve(expression, playerId, values)` | значение одного плейсхолдера; `null`, если он неизвестен |
| `render(template, playerId, values)` | подставляет все плейсхолдеры в текст |
| `update(expression, value, playerId, values)` | меняет записываемое значение |
| `contains(expression)` | есть ли такой видимый плейсхолдер |
| `provider(pluginId)` | доступ к ключам одного плагина |

Все методы возвращают `CompletionStage` и безопасны из любых потоков. Резолверы и форматтеры могут вызываться параллельно, поэтому должны быть потокобезопасны.

## Форматирование

`formatter(name, type, formatter)` регистрирует именованный формат для значений типа `type`. Форматтер получает значение, аргументы выражения и исходный запрос.

## Адаптеры

`adapters()` отдаёт реестр адаптеров к внешним системам (например, PlaceholderAPI). Возможности и состояние адаптера описывают `PlaceholderAdapterCapabilities` и `PlaceholderAdapterState`.
