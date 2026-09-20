# Локализация Minecraft

`pnlibrary-minecraft-localization` — необязательный модуль для разработчиков. Он получает официальные таблицы Minecraft по указанной версии и позволяет переводить ключи либо находить Bukkit-объекты по локализованным названиям.

Модуль ничего не скачивает при создании или запуске pnLibrary. Минимальная единица загрузки — один язык одной версии: Mojang публикует язык единым файлом.

## Подключение

```kotlin
dependencies {
    compileOnly("io.github.pnfolder:pnlibrary-minecraft-localization:2.2.0-beta.2")
}
```

## Создание и список версий

```java
MinecraftLocalization localization = MinecraftLocalization.builder()
    .cacheDirectory(dataFolder.resolve("translations"))
    .build();

List<MinecraftVersion> versions = localization.availableVersions()
    .toCompletableFuture()
    .join();
```

Manifest версий обновляется не чаще одного раза в 24 часа. Без сети используется корректный кэш, а при его отсутствии — `MinecraftVersion.supported()`.

## Явная загрузка

```java
TranslationBundle bundle = localization.load(
    TranslationRequest.builder()
        .version(MinecraftVersion.V1_21_4)
        .locales("ru_ru", "de_de")
        .fallback("en_us")
        .build()
).toCompletableFuture().join();
```

Скачиваются только `ru_ru`, `de_de` и явно указанный fallback `en_us`. Повторный вызов использует память, а после перезапуска — дисковый кэш.

Kotlin использует тот же контракт:

```kotlin
val bundle = localization.load {
    version = MinecraftVersion.V1_21_4
    locales("ru_ru", "de_de")
    fallback("en_us")
}.toCompletableFuture().join()
```

## Перевод и поиск

```java
LocaleTranslations russian = bundle.locale("ru_ru");

String sword = russian.translate("item.minecraft.diamond_sword")
    .orElse("item.minecraft.diamond_sword");

List<TranslationMatch<Material>> exact = russian.materials()
    .findExact("Алмазный меч");

List<TranslationMatch<Material>> search = russian.materials()
    .search("меч");
```

Поиск не предполагает уникальность названий и всегда возвращает список. Он игнорирует регистр, повторные пробелы и различие `ё/е`. Неизвестные текущему Bukkit API ключи остаются доступны через `keys()`.

## Кэш и завершение

Файлы находятся в `<cacheDirectory>/minecraft/translations/<version>/<locale>.json`. Загрузки проверяются по размеру и SHA-1 из официального asset index, затем публикуются атомарно. Параллельные запросы одного языка объединяются.

```java
localization.close();
```

Закрытие освобождает принадлежащий сервису executor и память, но не удаляет дисковый кэш.
