# Подключение pnLibrary к плагину

На сервер устанавливается один платформенный runtime. Плагин подключает
`pnlibrary-api` как `compileOnly` и один раз регистрируется. Стабильный ID и
metadata библиотека получает из нативного описания плагина.

```kotlin
dependencies {
    compileOnly("ru.privatenull:pnlibrary-api:2.0.0-beta.6")
}
```

## Единый контекст

```kotlin
private lateinit var pn: PnLibrary
private lateinit var context: PluginContext

override fun onEnable() {
    pn = PnLibraryProvider.get()
    context = pn.plugins.register(this) {
        it.metrics(12345, enabled = true) { metrics ->
            metrics.simplePie("storage_type") { database.type }
            metrics.singleLineChart("active_lots") { auction.activeLots }
        }
        it.listener(MarketListener())
        it.diagnostics(dataFolder.toPath(), diagnosticContainer)
        it.updates("pnFolder", "pnMarket") { updates ->
            updates.channel(UpdateChannel.STABLE)
                .automaticDownload(true)
                .artifact("(?i)^pnMarket-.*\\.jar$", minimumJava = 17)
        }
    }

    val message = context.lifecycle.enabled()
        .ok("Configuration", "loaded")
    if (database.isConnected) message.ok("Database", "connected")
    else message.warn("Database", "offline")
    message.show()
}

override fun onDisable() {
    val savedLots = auction.saveAll()
    context.close()
    context.lifecycle.disabled()
        .ok("Storage", "saved lots: $savedLots")
        .show()
}
```

`enabled()` и `disabled()` возвращают готовый MBox со стандартными строками ID,
версии, платформы, Java и сервисов. Дополнительные строки накапливаются в памяти,
а весь блок печатается только после явного `show()`. Момент показа контролирует
плагин. `context.close()` только освобождает принадлежащие контексту ресурсы.

Повторная регистрация занятого ID отклоняется. Регистр не зависит от регистра
букв и пробелов по краям: `pn.plugins.require("PNMARKET")` вернёт тот же контекст.

## Использование

```kotlin
val market = pn.plugins.require("pnmarket")

market.logger.success("Market loaded")
AuctionCreatedEvent(auctionId).callEvent().thenAccept { allowed ->
    if (!allowed) logger.warn("Создание аукциона отменено")
}
market.tasks.async(Runnable { repository.cleanup() })

market.metadata.version
market.metadata.javaVersion
market.metadata.javaFeature
market.metadata.platformImplementation

val report = market.messages.box("AUCTION CACHE")
    .ok("Loaded", "1,250 lots")
    .warn("Expired", "12 lots removed")
// Rows are buffered; this prints the complete box once.
report.show()

market.metrics.disable()
market.metrics.enable()
market.metrics.changeProjectId(54321)
```

При перезапуске metrics-сессии ранее добавленные диаграммы регистрируются снова.

```kotlin
class MarketListener : Listener {
    @EventHandler(priority = 250)
    fun created(event: AuctionCreatedEvent) {
        logger.info("Создан аукцион ${event.id}")
    }
}

data class AuctionCreatedEvent(val id: Long) : Event()
```

Async event example; the event mode chooses the execution context:

```kotlin
data class AuctionCacheLoadedEvent(val lots: Int) : Event(EventMode.ASYNC)

AuctionCacheLoadedEvent(auction.activeLots).callEvent()
```

Нативный объект плагина передаётся только один раз в `register`: он нужен
адаптеру для logger, scheduler, bStats и updater. В event bus подписки принадлежат
`PluginId`, поэтому их модель одинакова на Bukkit, BungeeCord и Velocity.
