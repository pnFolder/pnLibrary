# Кулдауны

Хранилище кулдаунов своё у каждого модуля: `context.cooldowns`. Время идёт по монотонным часам, просроченные записи сами
уходят из памяти.

## Просто

Имя, длину и право обхода задают один раз, а в команде пишут одну строку:

```kotlin
val home = context.cooldowns.cooldown("home", Duration.ofSeconds(30), bypassPermission = "shop.home.nocooldown")

command("home") {
    execute { ctx ->
        home.require(ctx.sender)      // слишком рано: команда останавливается, игрок читает, сколько ждать
        teleportHome(ctx.sender)
    }
}
```

## Со своим сообщением, своими действиями и настройками

```kotlin
val kit = context.cooldowns.cooldown("kit") { c ->
    c.duration(Duration.ofHours(1))
    c.tier("shop.donor", Duration.ofMinutes(20))     // донору короче; побеждает первый подходящий tier по порядку записи
    c.bypass("shop.admin")                           // админ не ждёт никогда
    c.onDenied { d -> d.who.sendActionBar(Component.text("Набор снова через ${d.remainingSeconds} с")) }
    c.onStarted { s -> logger.info("${s.who.name} взял набор, следующий через ${s.duration}") }
}

kit.run(player) { giveKit(player) }     // действие выполняется, только если можно; иначе твой onDenied (или сообщение библиотеки)
```

| Настройка | Что делает |
|---|---|
| `duration(...)` | общая длина, обязательна |
| `bypass("a", "b", ...)` | с любым из этих прав кулдауна нет; у консоли его нет никогда; вызовы складываются |
| `bypassIf { who -> ... }` | обход по своему условию, которое право не выразит (мир, состояние, звание) |
| `tier("право", длина)` | у кого есть право, ждёт другое время: короче или длиннее общей |
| `tier(listOf("a", "b"), длина)` | то же, но достаточно любого из прав |
| `tier("право", 0.5)` / `tier(listOf(..), 2.0)` | не время, а множитель к общей длине: `0.5` вдвое короче, `2.0` вдвое дольше |
| `pick(TierPick.X)` | какая ступень работает, если у игрока их несколько: `FIRST` (первая по порядку записи, по умолчанию), `SHORTEST` (самая короткая: все бонусы считаются), `LONGEST` (самая длинная: штраф не отменить бонусом) |
| `shared()` | один таймер на всех: кто взял первым, тот держит остальных |
| `onDenied { d -> }` | своё действие, когда игрока держат (`d.who`, `d.action`, `d.remaining`, `d.remainingSeconds`) |
| `onStarted { s -> }` | своё действие при старте (`s.who`, `s.action`, `s.duration` уже с учётом ступени) |

Пример «где-то больше, где-то меньше»:
```kotlin
context.cooldowns.cooldown("kit") { c ->
    c.duration(Duration.ofMinutes(10))                          // у всех по умолчанию
    c.tier(listOf("shop.vip", "shop.donor"), Duration.ofMinutes(2))   // любое из двух: короче
    c.tier("shop.newbie", 2.0)                                  // новичкам вдвое дольше
    c.pick(TierPick.SHORTEST)                                   // если подошло несколько, берём самую короткую
    c.bypass("shop.admin", "shop.staff")                        // любое из двух: ждать не надо
}
```

Три способа использовать, под разные случаи:
- `require(who)` останавливает команду стандартным ответом библиотеки;
- `run(who) { ... }` выполняет действие, а при отказе зовёт твой `onDenied`;
- `acquire(who)` ничего не делает сам, а возвращает `CooldownResult` (`allowed`, `isDenied`, `remaining`, `remainingSeconds`, `orThrow()`), дальше решаешь ты.

Ещё: `remaining(who)`, `isReady(who)`, `reset(who)`, `durationFor(who)`. `who` это любой Adventure `Audience`, UUID берётся из указателя.

Низкоуровневый доступ без объекта остаётся: `acquire`, `has`, `remaining`, `set`, `extend`, `reset`, `clear`, `active`.

## Для администратора

`/pn cooldowns <игрок>` показывает всё, что держит игрока, по всем плагинам и модулям (владелец, действие, секунды), самое долгое
первым. `/pn cooldowns <игрок> clear` сбрасывает их. Право `pnlibrary.cooldowns`.
