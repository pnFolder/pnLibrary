# Консольные карточки

Маленький модуль на Java без зависимостей: рисует в консоли карточки и деревья в едином стиле плагинов pnFolder.
pnLibrary использует его сама для своих сообщений о запуске и состоянии. Плагин, которому нужны такие же карточки,
встраивает модуль к себе и переносит его пакет, чтобы не столкнуться с копией внутри pnLibrary:

```kotlin
dependencies {
    implementation("io.github.pnfolder:pnlibrary-console:<версия>")
}

tasks.shadowJar {
    relocate("org.pnfolder.pnlibrary.console", "your.plugin.libs.pnlibrary.console")
}
```

Модуль не знает о платформе: цвета и способ вывода передаёт плагин. Пример для Bukkit:

```java
ConsoleTheme theme = new ConsoleTheme(
    ChatColor.DARK_GREEN.toString(),
    ChatColor.GREEN.toString(),
    ChatColor.WHITE.toString(),
    ChatColor.GRAY.toString(),
    ChatColor.RESET.toString()
);

ConsoleCard.builder(theme, "PLUGIN ГОТОВ")
    .mascot("( ^.^ )", "pnCases запущен", "все системы подключены")
    .blank()
    .detail("Версия", getDescription().getVersion())
    .lastDetail("Состояние", "включён")
    .blank()
    .status("Запуск завершён")
    .build()
    .send(getServer().getConsoleSender()::sendMessage);
```

| Класс | Что делает |
|---|---|
| `ConsoleTheme` | пять стилей: рамка, акцент, основной текст, второстепенный текст, сброс; пустые строки дают текст без цвета |
| `ConsoleCard` | карточка с заголовком, строками «название: значение» и итоговой строкой |
| `ConsoleTree` | вложенное дерево пояснений внутри карточки |
| `ConsoleSink` | куда выводить готовые строки |
