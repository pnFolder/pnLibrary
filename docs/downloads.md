# Скачивание файлов

Плагин один раз объявляет файлы, которые ему нужны, а библиотека сама скачивает, проверяет и кладёт их на место.

```kotlin
setup.downloads(plugin.dataFolder.toPath()) { d ->
    d.file("geoip", "https://example.com/geoip.mmdb", "geoip/geoip.mmdb")   // коротко: адрес и путь в папке данных

    d.file("skins") { f ->                                                  // подробно
        f.url("https://example.com/skins.zip")
            .mirror("https://mirror.example.com/skins.zip")                 // если первый не отвечает
            .integrity(1_000_000L, "sha256...")                             // размер и контрольная сумма
            .optional()                                                     // без файла плагин всё равно работает
            .policy(DownloadPolicy.AUTOMATIC)                               // MANUAL | AUTOMATIC | FORCED
            .destination(DownloadDestination.CACHE, "skins.zip")
    }
}
```

| `DownloadPolicy` | Когда качается |
|---|---|
| `MANUAL` (по умолчанию) | только когда администратор попросит: `/pn downloads now`, `/pn deps`, меню |
| `AUTOMATIC` | сама, пока включена настройка `downloads.automatic` |
| `FORCED` | сама, даже если `downloads.automatic` выключена |

Библиотека проверяет HTTPS, размер и SHA-256 (лимит 512 МБ на файл, меняется `maxBytes`), пробует зеркала по порядку и кладёт
файл атомарно (сначала временный, потом на место).

## Что видит автор плагина

```kotlin
val downloads = context.downloads        // DownloadRegistration? (null, если плагин ничего не объявил)
downloads?.snapshots()                   // что в каком состоянии, с причиной
downloads?.downloadNow()                 // начать сейчас
downloads?.whenFinished { result -> ... }   // реакция на конец любой загрузки, в том числе той, что библиотека запустила сама
```

Состояния: `DECLARED`, `CURRENT`, `DOWNLOADING`, `STAGED`, `BLOCKED`, `FAILED`, `CLOSED`.

## Администратору

`/pn downloads` показывает все объявленные файлы по плагинам и модулям с состоянием, `/pn downloads now` запускает их.
Недостающие плагины-зависимости смотрятся и качаются через `/pn deps`. Право `pnlibrary.downloads`.
