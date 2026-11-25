# Стиль API pnLibrary

Взят у Paper (разбор исходников Paper API 1.21.11). Берём порядок, не размеры: у Paper `Player.java` 4015 строк, у нас файл до 300.

## Как устроено
1. **Один пакет — одно понятие в единственном числе.** Внутри подпакеты по роли (`registry/keys`, `lifecycle/event`). В пакете 5–30 файлов; меньше трёх — вливаем в соседа.
2. **Один тип в файле, файл назван как тип.** Проверяет `OneTypePerFileTest`.
3. **Интерфейс — контракт, реализация в `core`.** Интерфейс, который реализует только библиотека, помечен `@ApiStatus.NonExtendable`. Никаких `Impl` в пакетах API.
4. **Фабрики рядом с типом** (`Type.of(...)`, `Type.builder()`), без `*Factory`. В Kotlin это `companion` или функция верхнего уровня в файле по смыслу.
5. **Модель неизменяема**, к ней вложенный `Builder` и короткая перегрузка: `Lore.of(lines)` = `Lore.builder().lines(lines).build()`.
6. **Ключ знает свой реестр.** `TypedKey<T>` вместо строки; константы лежат в final-объекте `*Keys` (`FormatKeys.ROUND`) с KDoc на каждой.
7. **Необязательное умение — метод с умолчанием «не умею»**, как у Adventure: `OnlinePlayer.addEffect` даёт `Delivery.unsupported()`, `position()` даёт `null`, платформа переопределяет. Игрок на прокси — тот же `OnlinePlayer`. Отдельных интерфейсов-способностей и `feature<T>()` нет.
   **Понятие получает свой тип, если живёт без игрока** (`PotionEffect`, `Particle`, `Location` у Paper): у нас `api.world` — `StatusEffect`, `Particle`, `Position`, `ItemMatcher`, без приставки `Player`.
8. **События по области** (`events/player`, `events/server`), конструктор `internal`, общее в `Abstract*Event`.
9. **Регистрация принадлежит владельцу** (плагин или модуль) и снимается вместе с ним; владелец и видимость видны в списках.
10. **Имена:** `*Keys`, `*Event`, `*Registrar`, `*Provider`, `Typed*`; булевы без `get` (`isPersistent`), остальное `name()`, `key()`.
11. **KDoc:** первая фраза говорит, что это и что делает; пример на 3 строки; не пересказывает имя.
12. **Стабильность явная:** `@ApiStatus.Experimental` на новом, `@Deprecated(replaceWith)` на старом. Замороженное из `main` не двигаем.
13. **Результат действия — свой пакет `delivery`** (`Delivery`, `DeliveryStatus`, `Receipt`, `Outcome`), а типы эффектов, частиц и предметов — Adventure `Key`, не строки.
14. **`package-info`** на пакет: зачем он и где главный тип.

## Чего не берём у Paper
- Интерфейсы-«боги» на тысячи строк и дубли (`Bukkit.*` рядом с `Server.*`).
- `*Impl` и `Holder` с `ServiceLoader` в публичных пакетах; глобальное состояние. У нас зависимости через конструктор.
- Лестницы generic (`LifecycleEventType<? super O, ? extends E, ?>`), `Valued`/`NonValued`.
- Шум из `@Contract`, `@Unmodifiable`, `@Experimental` на каждом методе.
- Тысячи автогенерируемых констант.
- `sealed ... permits Impl` ради приватной реализации: в Kotlin хватает `internal` в `core`.
