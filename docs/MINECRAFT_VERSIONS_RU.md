# Версии Minecraft в pnLibrary

`MinecraftVersion` избавляет плагины от ручного разбора строк Paper, Folia,
Spigot, Purpur и Leaf. Система не сравнивает `enum.ordinal`: версии сравниваются
по числовым координатам `major.minor.patch`, поэтому порядок констант не влияет
на результат.

## Получение версии

```kotlin
val version = BukkitMinecraftVersion.current()
val original = BukkitMinecraftVersion.rawCurrent()
```

На новых ядрах сначала вызывается `Server#getMinecraftVersion()` через
reflection. На старых используется `Bukkit.getBukkitVersion()`. Значение можно
получить и из кэша:

```kotlin
ServerCapabilities.minecraftVersion
ServerCapabilities.rawMinecraftVersion
```

Если вышла ещё не добавленная версия, `current()` вернёт `UNKNOWN`, а
`rawCurrent()` сохранит настоящую строку ядра. Это позволяет включить безопасный
режим и записать точную версию в диагностику.

## Одна граница

```kotlin
version.isAtLeast(MinecraftVersion.V1_20_5)       // 1.20.5 и новее
version.isAtMost(MinecraftVersion.V1_12_2)        // 1.12.2 и старее
version.isNewerThan(MinecraftVersion.V1_19_4)     // строго новее
version.isOlderThan(MinecraftVersion.V1_13)       // строго старее
version.isSameOrNewerThan(MinecraftVersion.V1_20)
version.isSameOrOlderThan(MinecraftVersion.V1_16_5)
```

`isSameReleaseLine` сравнивает только первые две части. Например, `1.20.4` и
`1.20.6` принадлежат линии `1.20`:

```kotlin
version.isSameReleaseLine(MinecraftVersion.V1_20)
```

## Диапазон от версии до версии

Короткая Kotlin-запись создаёт диапазон с включёнными границами:

```kotlin
val legacy = MinecraftVersion.V1_8_8..MinecraftVersion.V1_12_2

if (version in legacy) {
    useLegacyMaterials()
}
```

То же без оператора:

```kotlin
val legacy = MinecraftVersionRange.between(
    MinecraftVersion.V1_8_8,
    MinecraftVersion.V1_12_2,
)

version.inRange(legacy)
legacy.contains(version)
legacy.excludes(version)
```

Открытый диапазон не включает границы:

```kotlin
val range = MinecraftVersionRange.betweenExclusive(
    MinecraftVersion.V1_16_5,
    MinecraftVersion.V1_20_6,
)
```

## Диапазон без одной границы

```kotlin
val modern = MinecraftVersionRange.atLeast(MinecraftVersion.V1_20_5)
val afterFlattening = MinecraftVersionRange.newerThan(MinecraftVersion.V1_12_2)
val legacy = MinecraftVersionRange.atMost(MinecraftVersion.V1_12_2)
val beforeComponents = MinecraftVersionRange.olderThan(MinecraftVersion.V1_20_5)
```

Гибкие варианты `from` и `until` позволяют указать включение границы:

```kotlin
MinecraftVersionRange.from(MinecraftVersion.V1_19, inclusive = false)
MinecraftVersionRange.until(MinecraftVersion.V1_16_5, inclusive = true)
```

Дополнительные фабрики:

```kotlin
MinecraftVersionRange.exact(MinecraftVersion.V1_21_11)
MinecraftVersionRange.allKnown()
MinecraftVersion.atLeast(MinecraftVersion.V1_20_5)
MinecraftVersion.atMost(MinecraftVersion.V1_12_2)
MinecraftVersion.range(MinecraftVersion.V1_8_8, MinecraftVersion.V1_12_2)
```

## Работа нескольких диапазонов

```kotlin
val supported = MinecraftVersion.V1_16_5..MinecraftVersion.V26_2
val dataComponents = MinecraftVersionRange.atLeast(MinecraftVersion.V1_20_5)

if (supported.overlaps(dataComponents)) {
    val common = supported.intersection(dataComponents)
}
```

- `overlaps` сообщает, пересекаются ли диапазоны.
- `intersection` возвращает общую часть или `null`.
- `isExact` сообщает, содержит ли диапазон ровно одну версию.
- `containsCurrent` проверяет непосредственно версию запущенного ядра.
- `toString` создаёт читаемое значение: `[1.8.8, 1.12.2]`, `>=1.20.5`.

## Пример адаптера

```kotlin
private val legacy = MinecraftVersion.V1_8_8..MinecraftVersion.V1_12_2
private val flattening = MinecraftVersion.V1_13..MinecraftVersion.V1_20_4
private val components = MinecraftVersionRange.atLeast(MinecraftVersion.V1_20_5)

fun createMenuIcon(): ItemStack {
    val version = BukkitMinecraftVersion.current()
    return when (version) {
        in legacy -> createLegacyIcon()
        in flattening -> createFlattenedIcon()
        in components -> createComponentIcon()
        else -> createSafeIcon()
    }
}
```

## Таблица методов

| Метод | Значение |
|---|---|
| `parse(text)` | Разобрать строку версии |
| `current()` | Определить enum текущего ядра |
| `rawCurrent()` | Получить исходную версию ядра |
| `isAtLeast(version)` | Эта версия не старее указанной |
| `isAtMost(version)` | Эта версия не новее указанной |
| `isNewerThan(version)` | Эта версия строго новее |
| `isOlderThan(version)` | Эта версия строго старее |
| `isBetween(min, max)` | Входит во включённый диапазон |
| `isSameReleaseLine(version)` | Совпадают major и minor |
| `rangeTo(version)` | Создать диапазон оператором `..` |
| `contains(version)` | Диапазон содержит версию |
| `containsCurrent()` | Диапазон содержит текущее ядро |
| `excludes(version)` | Диапазон не содержит версию |
| `overlaps(range)` | Диапазоны пересекаются |
| `intersection(range)` | Получить общую часть диапазонов |

`UNKNOWN` никогда не считается входящим в диапазон и запрещён как его граница.
Это исключает случайное включение несовместимого кода на неизвестном ядре.

## Использование из Java

Получение версии и обычная проверка:

```java
MinecraftVersion version = BukkitMinecraftVersion.current();

if (version.isAtLeast(MinecraftVersion.V1_20_5)) {
    enableDataComponents();
}

if (version == MinecraftVersion.UNKNOWN) {
    logger.warning("Неизвестная версия Minecraft: " + BukkitMinecraftVersion.rawCurrent());
}
```

Закрытый диапазон с обеими включёнными границами:

```java
MinecraftVersionRange legacy = MinecraftVersionRange.between(
    MinecraftVersion.V1_8_8,
    MinecraftVersion.V1_12_2
);

if (legacy.contains(version)) {
    enableLegacyInventoryAdapter();
}
```

Диапазоны с одной границей:

```java
MinecraftVersionRange modern = MinecraftVersionRange.atLeast(
    MinecraftVersion.V1_20_5
);

MinecraftVersionRange oldVersions = MinecraftVersionRange.atMost(
    MinecraftVersion.V1_12_2
);

MinecraftVersionRange strictlyNewer = MinecraftVersionRange.newerThan(
    MinecraftVersion.V1_19_4
);
```

Проверка текущего ядра без промежуточной переменной:

```java
if (modern.containsCurrent()) {
    registerModernListeners();
}
```

Пересечение диапазонов:

```java
MinecraftVersionRange pluginSupport = MinecraftVersionRange.between(
    MinecraftVersion.V1_16_5,
    MinecraftVersion.V26_2
);

MinecraftVersionRange featureSupport = MinecraftVersionRange.atLeast(
    MinecraftVersion.V1_20_5
);

MinecraftVersionRange common = pluginSupport.intersection(featureSupport);

if (common != null) {
    logger.info("Функция доступна на версиях: " + common);
}
```

Java не поддерживает Kotlin-оператор `..`, поэтому вместо него используется
`MinecraftVersionRange.between(...)` или `MinecraftVersion.range(...)`.
