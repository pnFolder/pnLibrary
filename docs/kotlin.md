# Kotlin

Библиотека написана на Java 8, и из Kotlin её API работает как есть: лямбды вместо интерфейсов, `library.proxy` вместо `getProxy()`, типы со знаком `?` по аннотациям `@Nullable`. Модуль `pnlibrary-kotlin` только убирает неудобное. Плагинам на Java он не нужен, в jar библиотеки он не входит (`kotlin-stdlib` берёт сам плагин).

```kotlin
dependencies {
    compileOnly("io.github.pnfolder:pnlibrary-api:<версия>")
    implementation("io.github.pnfolder:pnlibrary-kotlin:<версия>")
}
```

## Что внутри

Класс вместо `Foo::class.java`:
```kotlin
val shop = library.services.get<ShopApi>()           // ShopApi?
val bank = library.services.require<BankApi>()       // throws, если нет
val all = library.services.all<Payment>()
library.services.register<ShopApi>(myShop, priority = 5)
library.services.unregister<ShopApi>()
val server = library.platforms.get<org.bukkit.Server>()
val papi = library.expansions.get<MyExpansion>()
```
Подписка на события:
```kotlin
scope.subscribe<PlayerBought> { event -> log(event) }
scope.subscribe<PlayerBought>(priority = 250) { event -> log(event) }
```
Кодек сообщения из двух лямбд (писатель и читатель в них как `this`):
```kotlin
val balance = messageCodec<Balance>(
    encode = { b -> writeUuid(b.id).writeVarInt(b.coins) },
    decode = { Balance(readUuid(), readVarInt()) },   // тот же порядок
)
val channel = library.serverNetwork!!.channel(Key.key("myplugin", "balance")).with(MessageCodec.compressed(balance))
```
Сообщение с типом, писатель как `this`:
```kotlin
channel.sendMessage(server, "balance") { writeUuid(id).writeInt(250) }    // прокси
serverChannel.sendMessage("balance") { writeUuid(id).writeInt(250) }      // игровой сервер
```
Команда без `it.`:
```kotlin
val shop = command("shop") {
    permission("shop.use")
    executes { ctx -> open(ctx) }
    sub("buy") {
        arg("item", ArgumentType.string()) { executes { ctx -> buy(ctx, ctx.arg<String>("item")) } }
    }
}
```
Время кулдауна в Kotlin-длительностях:
```kotlin
val kit = library.cooldowns.define("kit") {
    duration(30.minutes)
    tier("kit.vip", 10.minutes)
    bypass("kit.bypass")
}
```
Для ожидания результата корутиной (`CompletionStage<Boolean>` с `send`) отдельной помощи не нужно: `kotlinx.coroutines.future.await()` из самих корутин уже работает с ним.

Остальное (`listOf`, `split`, `compressed`, каналы) работает как в [channels.md](channels.md).

## Для разработчиков библиотеки

Модуль `modules/kotlin`, пакет `org.pnfolder.pnlibrary.kotlin`, один файл на область (`Services.kt`, `Events.kt`, `Codecs.kt`). Режим `explicitApi`, цель JVM 8. Правила те же: ничего, что нельзя сделать из Java, кроме удобства. Бинарная совместимость: `modules/kotlin/api/kotlin.api` (`apiDump`, затем `apiCheck`). Новые расширения добавлять только там, где Kotlin действительно неудобен, чтобы не плодить второй API.
