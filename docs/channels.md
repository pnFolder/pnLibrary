# Каналы плагинных сообщений

Канал связывает прокси и игровые серверы: одна сторона отправляет байты, другая получает. Работает на Bungee, Velocity и Bukkit. Канал называется ключом Adventure (`net.kyori.adventure.key.Key`): пространство и имя строчными буквами, `Key.key("myplugin", "players")`. Пространство `minecraft` занято игрой, и `Key.key("main")` без пространства даёт именно его, поэтому такой канал не откроется. Ключ сам проверяет символы.

## Где что лежит

| Пакет | Что внутри |
|---|---|
| `api.network` | `NetworkProxy`, `NetworkServer`, `NetworkPlayer` (старый API, заморожен) |
| `api.network.channel` | каналы: `NetworkChannel` (прокси), `ServerChannel` (игровой сервер), `ServerNetwork`, `Channels`, `MessageChannel` |
| `api.network.channel.typed` | общая основа `NetworkChannelOf<D>` и `ServerChannelOf<D>` (`NetworkChannel` и `ServerChannel` это они же для `byte[]`), обработчик по типу `MessageListener` |
| `api.network.message` | запись и чтение байтов: `MessageWriter`, `MessageReader`, `MessageCodec`, сжатие |

Входы: на прокси `library.getProxy().channel(имя)`, на игровом сервере `library.getServerNetwork().channel(имя)`. На прокси `getServerNetwork()` даёт `null`, на сервере `getProxy()` тоже. Канал на одно имя один; `channels()` показывает открытые. Закрыть: `close()`.

Просмотр открытых каналов: `/pnproxy channels` на прокси и `/pn channels` на игровом сервере (право `pnlibrary.channels`); страница «Каналы прокси» есть в `/pn gui`.

## 1. Сырые байты

Прокси:
```java
NetworkChannel channel = library.getProxy().channel(Key.key("myplugin", "main"));
channel.listen((from, data) -> log.info(from.getName() + " прислал " + data.length + " байт"));
channel.send(server, new byte[] {1, 2, 3});   // false, если сервер недоступен
channel.broadcast(new byte[] {4});            // сколько серверов получили
```
Игровой сервер:
```java
ServerChannel channel = library.getServerNetwork().channel(Key.key("myplugin", "main"));
channel.listen(data -> log.info("от прокси " + data.length + " байт"));
channel.send(new byte[] {1, 2, 3});   // ждёт игрока: в очереди до 100 сообщений
```
Игровой сервер отправляет через любого онлайн-игрока. Пока никого нет, сообщения стоят в очереди и уходят при входе первого. Сообщения от игроков на открытых каналах отбрасываются.

## 2. Сообщение с типом

Тип пишется первой строкой, чужие и неизвестные типы игнорируются:
```java
// прокси
channel.send(server, "balance", msg -> msg.writeUuid(id).writeInt(500));
channel.on("balance", (from, msg) -> {
    UUID id = msg.readUuid();
    int coins = msg.readInt();
});
```
`on` держится до закрытия канала. Чтобы снять его раньше, возьмите ручку: `Runnable stop = channel.on(...); stop.run();`.

## 3. Свой класс: кодек

Один кодек пишет и читает класс. Его используют обе стороны, без рефлексии и без типа на проводе.

```java
public final class Balance {
    public final UUID id;
    public final int coins;
    public Balance(UUID id, int coins) { this.id = id; this.coins = coins; }
}

public final class BalanceCodec implements MessageCodec<Balance> {
    @Override
    public void encode(Balance value, MessageWriter out) {
        out.writeUuid(value.id).writeVarInt(value.coins);
    }

    @Override
    public Balance decode(MessageReader in) {
        return new Balance(in.readUuid(), in.readVarInt());   // порядок как при записи
    }
}
```
Использование:
```java
// прокси
NetworkChannelOf<Balance> channel = library.getProxy().channel(Key.key("myplugin", "balance")).with(new BalanceCodec());
channel.listen((from, balance) -> save(balance));
channel.send(server, new Balance(id, 500));

// игровой сервер
ServerChannelOf<Balance> channel = library.getServerNetwork().channel(Key.key("myplugin", "balance")).with(new BalanceCodec());
channel.listen(balance -> apply(balance));
channel.send(new Balance(id, 500));
```
Кодек можно проверить без сети: `codec.fromBytes(codec.toBytes(value))`.

## 4. Лимит размера и экономия

Одно сообщение не больше `MessageChannel.MAX_MESSAGE_BYTES` = 32766 байт (лимит Bukkit). Больше бросает `IllegalArgumentException` до отправки.

| Тип | Байт |
|---|---|
| `writeBoolean`, `writeByte` | 1 |
| `writeShort` | 2 |
| `writeInt`, `writeFloat` | 4 |
| `writeLong`, `writeDouble` | 8 |
| `writeUuid` | 16 |
| `writeText` | 2 + UTF-8 (кириллица 2 байта на символ) |
| `writeVarInt` (только неотрицательные) | 1–5, малые числа короче |
| `writeBlock` | 4 + длина |

Как уместить больше:
- **Узкие типы:** `short`/`byte` вместо `int`, `writeVarInt` для малых чисел.
- **Биты в одном числе.** Координаты x и z от -1000 до 999 (11 бит каждая) и y от 0 до 127 (7 бит) помещаются в один `int`:
  ```java
  int place = (x + 1000) << 18 | (z + 1000) << 7 | y;
  // чтение
  int y = place & 0x7F;
  int z = ((place >>> 7) & 0x7FF) - 1000;
  int x = (place >>> 18) - 1000;
  ```
  Значение за пределами диапазона портит соседнее поле, поэтому в `encode` проверяйте границы.
- **Столбцы:** в списке писать не объект за объектом, а сначала все UUID, потом все имена, потом все числа. Так лучше работает сжатие.
- **Сжатие:** `MessageCodec.compressed(codec)` (deflate, потолок 1 МБ после распаковки). Оборачивать надо на обеих сторонах одинаково. Выгодно для больших списков с повторами; мелкие и случайные данные оставляет как есть.

Пример для 32766 байт, игрок с UUID, именем из 13 символов, координатами, уровнем и флагом `op`:

| Способ | Игроков влезает | С `compressed` |
|---|---|---|
| Обычная запись (48 байт на игрока) | 682 | 1142 |
| Столбцы, узкие типы | 885 | 1335 |
| Биты в одном числе | 910 | 1374 |

Список из многих объектов: `MessageCodec.listOf(кодек)` пишет длину и объекты подряд, а `кодек.split(список)` режет его на части, каждая из которых влезает в сообщение:
```java
MessageCodec<Row> rowCodec = new RowCodec();                 // один объект
NetworkChannelOf<List<Row>> channel = ...with(MessageCodec.listOf(rowCodec));
for (List<Row> part : rowCodec.split(rows)) {
    channel.send(server, part);                              // каждая часть до 32766 байт
}
```
`split` меряет каждый объект кодеком, поэтому подходит для записи «объект за объектом» (для столбцов нет). Один объект больше лимита даёт `IllegalArgumentException`.

## Ошибки и порядок

- Ошибка слушателя пишется в журнал `pnLibrary`, остальные слушатели получают сообщение.
- Кодек, который не смог прочитать сообщение, пишет предупреждение и пропускает его.
- Закрытый канал можно открыть заново тем же именем.
