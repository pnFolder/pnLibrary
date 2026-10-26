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
