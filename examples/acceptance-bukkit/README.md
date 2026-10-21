# Приёмочный плагин (Bukkit)

Плагин для ручной проверки pnLibrary на настоящем сервере Bukkit или Paper. Он отделён от `demo-bukkit` намеренно:
демо показывает, как писать плагин, а этот плагин даёт переключатели и команды, чтобы проверить подсистемы по
одной.

## Сборка

```text
gradlew.bat :examples:acceptance-bukkit:assembleAcceptanceKit
```

Готовая пара jar-файлов появится в корневой папке `build/acceptance-bukkit`. Скопируйте обе в `plugins`:

- `pnLibrary-<версия>-bukkit-java8.jar`
- `pnLibrary-acceptance-bukkit-<версия>.jar`

Запустите сервер и выполните `/pnaccept status`. Проверки, которые ходят в сеть, выключены в
`plugins/pnLibraryAcceptance/config.yml`. Включайте их по одной и перезапускайте сервер.

## Проверки

| Команда | Что проверяет |
|---|---|
| `/pnaccept event` | шину событий |
| `/pnaccept task` | планировщик задач |
| `/pnaccept cooldown` (дважды) | кулдауны |
| `/pnaccept metrics` | жизненный цикл метрик |
| `/pnaccept diagnostics`, затем `/pndebug acceptance --local` | сбор диагностики |
| `/pnaccept update` | обновления (после включения в `config.yml`) |
| `/pnaccept download` | загрузку дополнительных файлов (после включения в `config.yml`) |

## Политика

Готовые политики на Java и Kotlin лежат в `policies/AcceptancePolicy.java` и `policies/AcceptancePolicy.kt`. Они
требуют версию плагина `2.3.0`, поэтому текущий приёмочный плагин (`2.2.0-beta.2`) получит отказ.

Для локальной проверки включите политику в `config.yml` и укажите абсолютный путь к файлу:

```yaml
remote-policy:
  enabled: true
  on-deny: DISABLE_PLUGIN
  source: file:///C:/path/to/AcceptancePolicy.java
```

Для проверки через GitHub загрузите тот же файл и укажите его адрес
`https://raw.githubusercontent.com/.../AcceptancePolicy.java`.
