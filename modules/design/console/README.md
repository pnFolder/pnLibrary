# pnLibrary Console Style

Standalone, dependency-free renderer for the console-card style used by pnFolder plugins. It is not included in the pnLibrary runtime or distribution. Plugins opt in and shade it into their own JAR.

```kotlin
dependencies {
    implementation("io.github.pnfolder:pnlibrary-console-style:<version>")
}

tasks.shadowJar {
    relocate(
        "ru.privatenull.pnlibrary.console",
        "your.plugin.libs.pnlibrary.console",
    )
}
```

The renderer is platform-neutral. A Bukkit plugin supplies its colors and console sender:

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
