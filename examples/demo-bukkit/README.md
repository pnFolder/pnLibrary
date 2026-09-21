# pnLibrary Demo Bukkit

Это отдельный production-style демонстрационный плагин. Он не встраивает pnLibrary в свой JAR:
на сервере должен быть установлен обычный `pnLibrary` runtime, а demo подключается к нему через
`PnLibraryProvider` и `PluginContext`.

## Запуск

1. Соберите `:examples:demo-bukkit:build`.
2. Положите `examples/demo-bukkit/build/libs/pnLibrary-demo-bukkit-*.jar` в `plugins/` рядом
   с соответствующим `pnLibrary-*-bukkit-java8.jar`.
3. Запустите Paper/Spigot и выполните `/pndemo status`.

Команды:

- `/pndemo status` — показывает контекст и публикует тестовое событие;
- `/pndemo balance` — читает баланс игрока через Currency API;
- `/pndemo give <amount>` — демонстрирует мутацию валюты и cooldown;
- `/pndemo reload` — перезагружает конфигурацию demo.

При включении плагин регистрирует lifecycle summary, bStats-метрики, diagnostics snapshot,
typed placeholder `pndemo_coins`, повторяющуюся задачу, listener и lightweight currency.
