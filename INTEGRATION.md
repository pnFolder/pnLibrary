# Подключение pnLibrary к плагину

На сервер устанавливается один платформенный runtime. Плагин подключает
`pnlibrary-api` как `compileOnly` и один раз регистрируется под собственным ID.

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
    context = pn.plugins.register(this, "pnmarket") {
        it.metrics(12345, enabled = true) { metrics ->
            metrics.simplePie("storage_type") { database.type }
            metrics.singleLineChart("active_lots") { auction.activeLots }
        }
        it.listener(MarketListener())
        it.diagnostics(dataFolder.toPath(), diagnosticContainer)
        it.updates(updateRequest)
    }
}

override fun onDisable() {
    context.close()
}
```

`context.close()` закрывает события, задачи, метрики, диагностику и обновления.
Повторная регистрация занятого ID отклоняется. Регистр не зависит от регистра
букв и пробелов по краям: `pn.plugins.require("PNMARKET")` вернёт тот же контекст.

## Использование

```kotlin
val market = pn.plugins.require("pnmarket")

market.logger.success("Market loaded")
market.events.publish(AuctionCreatedEvent(auctionId))
market.tasks.async(Runnable { repository.cleanup() })

market.messageBox("pnMarket 1.0.5")
    .ok("Platform", pn.platform.type.name)
    .ok("Metrics", if (market.metrics.isEnabled) "enabled" else "disabled")
    .show()

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

Нативный объект плагина передаётся только один раз в `register`: он нужен
адаптеру для logger, scheduler, bStats и updater. В event bus подписки принадлежат
`PluginId`, поэтому их модель одинакова на Bukkit, BungeeCord и Velocity.
