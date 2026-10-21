# Пример удалённой политики

Удалённая политика — это один файл `.java` или `.kt`, который pnLibrary скачивает, компилирует и запускает, чтобы
решить, можно ли плагину или модулю работать. Так автор может, например, отключить устаревшую версию плагина у
всех серверов, не выпуская обновление.

Пример `MinimumVersionPolicy.java` пропускает версии плагина начиная с `2.4.0`. Более старые получают отказ:

```text
Установлена версия 2.3.1, требуется 2.4.0. Скачайте новую версию плагина.
```

## Как подключить

1. Опубликуйте файл политики по HTTPS, например как raw-файл на GitHub.
2. Укажите его при регистрации модуля:

```kotlin
val plugin = library.plugins.register(this)
plugin.registerModule("pncase") { builder ->
    builder.remotePolicy(
        RemotePolicySettings.builder()
            .source("https://raw.githubusercontent.com/<владелец>/<репозиторий>/main/MinimumVersionPolicy.java")
            .checkEvery(Duration.ofHours(6))
            .onDeny(DenyAction.DISABLE_MODULE)
            .build(),
    )
}
```

Пакет и имя класса библиотека читает из самого файла, в плагине их писать не нужно. `onDeny` выбирает, что
выключить при отказе: модуль (`DISABLE_MODULE`) или весь плагин (`DISABLE_PLUGIN`). `checkEvery` задаёт, как
часто политика проверяется заново.

Политика реализует `org.pnfolder.pnlibrary.api.remote.RemotePolicy` и возвращает
`RemotePolicyResult.allow()` или `RemotePolicyResult.deny("причина")`.

Проверить, что пример компилируется:

```text
gradlew :examples:remote-policy:build
```
