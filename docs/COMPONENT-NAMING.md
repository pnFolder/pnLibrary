# Правила именования компонентов и JAR-файлов

## Основной формат

```text
<product>-<version>-<platform>-java<minimumJava>.jar
```

Пример:

```text
pnLibrary-2.3.0-bukkit-java8.jar
```

Части имени разделяются одним дефисом. Пробелы, подчёркивания и русские буквы не используются.

## Product

Идентификатор продукта пишется в нижнем регистре, в kebab-case:

```text
pnLibrary       → pnlibrary
pnAuth          → pnauth
My Economy      → my-economy
```

В имени JAR используется тот же идентификатор, что и в `component.json`:

```json
"product": "pnlibrary"
```

## Version

Используется полная SemVer-версия:

```text
2.3.0
2.3.0-alpha.1
2.3.0-beta.1
2.3.0-beta.2
```

Канал не добавляется отдельным суффиксом. Нельзя использовать формы вроде:

```text
pnLibrary-2.3.0-B.jar
pnLibrary-2.3.0-beta.jar
```

Номер beta или alpha должен быть частью версии: `2.3.0-beta.2`.

Поддерживаемые каналы pnLibrary:

```text
stable
beta
alpha
dev
```

Канал `rc` в текущей модели не используется. Если понадобится release candidate, его нужно отдельно добавить в API, а не маскировать под другой канал.

## Platform

Платформа использует фиксированный идентификатор:

```text
bukkit
paper
purpur
bungee
velocity
```

Если артефакт совместим с несколькими Bukkit-ядрами и не зависит от конкретного ядра, используется `bukkit`.

Для платформенного API, не имеющего версии Minecraft, не добавляется искусственная версия игры. Совместимость указывается внутри `component.json`, если она действительно нужна.

## Java

`java<minimumJava>` означает минимальную версию Java:

```text
java8
java17
java21
```

Примеры:

```text
pnLibrary-2.3.0-bukkit-java8.jar
pnAuth-2.4.0-velocity-java17.jar
```

Если в будущем появится несколько артефактов с разными верхними ограничениями Java, используется диапазон:

```text
<product>-<version>-<platform>-java<minimum>-<maximum>.jar
```

Например:

```text
pnAuth-2.4.0-bukkit-java17-21.jar
```

Но диапазон добавляется только при реальной необходимости. В обычном случае достаточно минимальной Java.

## Полные примеры

```text
pnLibrary-2.3.0-bukkit-java8.jar
pnLibrary-2.3.0-beta.2-bukkit-java8.jar
pnLibrary-2.3.0-velocity-java17.jar
pnAuth-2.4.0-bungee-java8.jar
pnAuth-2.4.0-velocity-java17.jar
```

## Что проверяет библиотека

1. Находит релиз нужного канала в GitHub.
2. Отбирает JAR по product, platform и Java.
3. Скачивает выбранный файл.
4. Проверяет размер и SHA-256 по данным GitHub.
5. Читает `META-INF/pnlibrary/component.json`.
6. Сверяет product, version, API и дополнительные ограничения.

Имя файла помогает выбрать кандидата, но не является окончательным источником доверия. Окончательная проверка выполняется по metadata внутри JAR.
