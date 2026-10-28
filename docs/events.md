# События в pnLibrary

Одна шина событий работает на Bukkit, BungeeCord и Velocity. Событие и слушатель зависят только от `pnlibrary-api`,
поэтому один и тот же код запускается на любой платформе.

Важно знать сразу:

- Вызов **синхронный**: `callEvent()` возвращается, когда отработали все слушатели, и поля или результат события уже
  окончательные.
- Слушатели выполняются **в потоке вызывающего**. Хотите звать событие из фона, делайте это явно (`callEventAsync` с
  нужным `Executor`).
- Сломанный слушатель логируется и пропускается, остальные работают дальше.
- События не «отменяются». Если событие умеет запрещаться, у него есть результат (`ResultedEvent`), и слушатель его
  меняет.

## 1. Своё событие

```kotlin
class ShopPurchaseEvent(val buyer: LibraryPlayer, val price: Int) : ResultedEvent<ResultedEvent.ComponentResult>() {
    override var result: ResultedEvent.ComponentResult = ResultedEvent.ComponentResult.allowed()
}
```

Если запрещать не нужно, наследуйтесь от `Event`: это просто уведомление.

## 2. Слушатель

Подписка с типом события и без лишнего кода:

```kotlin
val events = module.events   // EventScope вашего модуля; закрывается вместе с ним

events.subscribe<ShopPurchaseEvent>(priority = EventPriority.HIGH) { event ->
    if (event.price > 1000) event.result = ResultedEvent.ComponentResult.denied(Component.text("Слишком дорого"))
}
```

Цепочкой, если нужны параметры по одному:

```kotlin
events.on<ShopPurchaseEvent>().priority(EventPriority.MONITOR).ignoreDenied().listen { log(it) }
```

Классом с аннотациями (метод принимает ровно одно событие и возвращает `Unit`/`void`):

```kotlin
class ShopListener : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreDenied = true)
    fun onPurchase(event: ShopPurchaseEvent) { /* ... */ }
}

val registration = events.register(ShopListener())   // registration.close() снимает всё сразу
```

`register` бросает `IllegalArgumentException`, если в классе нет ни одного `@EventHandler`.

## 3. Вызвать событие

```kotlin
val event = ShopPurchaseEvent(player, 1500)
if (!event.callEvent()) {
    player.sendMessage(event.denyReason ?: Component.text("Отказано"))
    return
}
```

- `callEvent()` возвращает `event.isAllowed`. Событие без результата всегда разрешено.
- `callResult()` у `ResultedEvent` возвращает сам типизированный результат, если в нём больше данных, чем да/нет.
- `denyReason` читается, только если результат `ComponentResult`.

## 4. Приоритеты

Меньшее число идёт раньше. Есть ориентиры: `LOWEST -1000`, `LOW -500`, `NORMAL 0`, `HIGH 500`, `HIGHEST 1000`,
`MONITOR 2000`. Можно любое целое, например `250`. При равных приоритетах порядок регистрации сохраняется. `MONITOR`
нужен, чтобы посмотреть итог: изменять событие там не запрещено технически, но не стоит.

`ignoreDenied = true` пропускает слушатель, если событие уже запрещено более ранним.

## 5. События игроков

Библиотека сама публикует `PlayerJoinedEvent` и `PlayerLeftEvent` (пакет `org.pnfolder.pnlibrary.api.events.player`) на
всех платформах. Это объявления: запретить вход или выход слушатель не может. В событии есть `uniqueId`, `name` и
`player` (`LibraryPlayer`, либо `null`, если платформа игрока уже не знает).

## 6. Из другого потока и ошибки

```kotlin
PnLibraryProvider.get().events.callEventAsync(event, executor)   // CompletionStage<E>: слушатели выполнятся на executor
```

Для работы с миром передайте executor, который переходит на главный поток сервера. Вызов события после закрытия шины и подписка
в закрытой области (`EventScope`) бросают `IllegalStateException`. Закрытие модуля снимает все его подписки.

## 7. Трассировка: кто что изменил

Если результат события оказался неожиданным, включите запись:

```kotlin
val events = PnLibraryProvider.get().events
events.inspector.enableTracing(ShopPurchaseEvent::class.java, capacity = 32)

ShopPurchaseEvent(player, 1500).callEvent()

events.inspector.traces().last().steps.forEach { step ->
    println("${step.plugin}/${step.module} priority=${step.priority} skipped=${step.skipped} failed=${step.failed} " +
        "${step.resultBefore} -> ${step.resultAfter} (${step.nanos} ns)")
}
events.inspector.disableTracing(ShopPurchaseEvent::class.java)
```

Для каждого слушателя видно, плагин и приоритет, пропущен ли он из-за `ignoreDenied`, упал ли он, сколько занял и
как изменил результат. Результаты сравниваются по `toString()`, поэтому дайте своему классу результата понятный
`toString()`. Для типов, которые не записываются, трассировка ничего не стоит.

## 8. Кто подписан на событие

Список слушателей доступен и тогда, когда событие ещё ни разу не вызывали:

```kotlin
val events = PnLibraryProvider.get().events

events.inspector.listenerCount(ShopPurchaseEvent::class.java)          // сколько слушателей получат событие
events.inspector.listeners(ShopPurchaseEvent::class.java).forEach {     // в порядке вызова
    println("${it.plugin}/${it.module} priority=${it.priority} ignoreDenied=${it.ignoreDenied} ${it.source} " +
        "runs=${it.invocations} failures=${it.failures}")
}
events.inspector.listeners()                                            // все слушатели всех плагинов
```

В списке для события есть и слушатели его родительских классов, потому что они тоже его получат. `source` показывает
`Класс#метод` для `@EventHandler` или класс лямбды. Счётчики `invocations` и `failures` ведутся с момента регистрации.

Все эти методы собраны в отдельном классе `EventInspector` (`events.inspector`). Там же:

```kotlin
val bus = PnLibraryProvider.get().events.inspector
bus.events()                                     // каждое событие: слушателей, вызовов, отказов
bus.plugins(ShopPurchaseEvent::class.java)       // какие плагины слушают
bus.listeners(PluginId.of("shop"), ModuleId.of("cart"))  // слушатели одного модуля
```

В игре и в консоли то же самое показывает команда `/pn bus` (право `pnlibrary.events.bus`).

## 9. История вызовов по дням

pnLibrary считает, сколько раз в какой день вызвали каждое событие и сколько вызовов были отклонены. Счётчики лежат в
`event-history.tsv` в папке данных и переживают перезапуск; запись идёт в фоне не чаще раза в минуту и при остановке.

```kotlin
val bus = PnLibraryProvider.get().events.inspector
bus.historyDays                                   // сколько дней хранится
bus.history().forEach { println("${it.date} ${it.eventType} ${it.calls} ${it.denied}") }
bus.history("com.shop.ShopPurchaseEvent")         // только одно событие, новые дни первыми
```

Срок хранения задаётся только в коде: `PnLibraryConfig(eventHistoryDays = 90)` (по умолчанию 30, от 0 до 3650;
`0` отключает историю). В файле настроек pnLibrary этого параметра нет, пользователи его не меняют. Старые дни удаляются сами.

В игре и консоли: `/pn bus history [страница]` показывает дни «Сегодня», «Вчера» и даты, а `/pn bus history of <событие>`
только одно событие. Строки с полосками, клик по строке открывает событие.
