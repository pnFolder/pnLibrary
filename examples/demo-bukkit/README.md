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
- `/pndemo-lib status` (`/pndemoapi status`) — та же проверка через portable Command API pnLibrary;
- `/pndemo-lib give <amount>` — аргумент с типизированным parsing и асинхронным ответом.

При включении плагин регистрирует lifecycle summary, bStats-метрики, diagnostics snapshot,
typed placeholder `pndemo_coins`, повторяющуюся задачу, listener и lightweight currency.
Команда `pndemo-lib` регистрируется самой библиотекой и не требует ручной записи в `plugin.yml`.
