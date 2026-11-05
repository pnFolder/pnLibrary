# Плейсхолдеры: рабочий пример

Каждая строка ниже проверена тестами `PlaceholderTreeTest` и `PlaceholderBuiltInFormattersTest`. Правила и таблицы:
[placeholders.md](placeholders.md).

```kotlin
import org.pnfolder.pnlibrary.api.placeholders.tree.*

// Данные, из которых генерируются ветки
data class Clan(val name: String, val tag: String, val members: Int, val bank: Double, val leader: String)

class ClansPlugin : JavaPlugin() {                       // id плагина: "pnclans"
    override fun onEnable() {
        val library = PnLibraryProvider.get()
        val module = library.plugins.register(this).registerModule("war")

        // 1. Свои форматы модуля. Снаружи: {…|pnclans/war:rank}
        module.formats {
            format<Int>("rank") { "#$it" }                                          // 3      -> #3
            format<Int>("stars") { n -> "★".repeat(n.coerceIn(0, 5)) }              // 3      -> ★★★
            format<Int>("pad0") { n, args -> n.toString().padStart(args.first().toInt(), '0') }
        }

        // 2. Плейсхолдеры. papi = "clans": всё опубликуется как %clans_war~путь%
        module.placeholders(papi = "clans") {
            format("group")                                   // формат по умолчанию для всей группы

            int("score") {                                    // {pnclans/war:score}            %clans_war~score%
                value { clans.scoreOf(player) }
                player("who") {                               // {pnclans/war:score.Alex}       ник или UUID
                    value { clans.scoreOf(arg) }
                    int("kills") { value { clans.killsOf(arg) } }        // {pnclans/war:score.Alex.kills}
                }
                int("top") {                                  // {pnclans/war:score.top}
                    value { clans.topScore(1) }
                    param("place", int, range = 1..100) {     // {pnclans/war:score.top.3}
                        value { clans.topScore(arg) }
                    }
                }
            }

            int("id") { format(none); value { clans.idOf(player) } }       // без формата: 1234, а не 1 234
            text("name") { format("upper"); value { "wolf" } }

            // Читать может только pnshop; менять значение — только он же
            number("bank") {
                access { allowOnly("pnshop") }
                value { clans.bank(player) }
            }

            // Только для {…}, в PlaceholderAPI не уходит
            int("debug") { inside(); value { clans.debugCounter() } }

            // Ветка на каждое свойство класса
            from<Clan>("clan") { clans.clanOf(player) }
            //   {pnclans/war:clan.name}  clan.tag  clan.members  clan.bank  clan.leader

            // Лишнее убрать, поле переименовать
            from<Clan>("mini") { clans.clanOf(player) }
                .only(Clan::name, Clan::members)
                .rename(Clan::members, "size")                //   mini.name, mini.size

            // По ключу и по месту
            table("price", keys = { shop.items() }) { item -> shop.priceOf(item) }   // price.sword
            list("top5", size = { 5 }) { place -> clans.topName(place) }             // top5.1 … top5.5
        }
    }
}
```

## Что получается в тексте

| Текст | Результат |
|---|---|
| `{pnclans/war:score}` | `1 234` (формат группы `group`) |
| `{pnclans/war:score\|raw}` | `1234` (формат по умолчанию отключён) |
| `{pnclans/war:score\|number}` | `1234` (формат из текста сильнее) |
| `{pnclans/war:score.top.3\|rank}` | `#…`, формат модуля |
| `{pnshop/main:price.sword\|pnclans/war:pad0:5}` | `00050`, чужой формат по адресу с аргументом |
| `{pnclans/war:id}` | `1234` (у ветки `format(none)`) |
| `{pnclans/war:clan.leader.name}` | вложенный объект раскрывается на один уровень |
| `{pnclans/war:nothing}` | остаётся в тексте как есть, причина в `/pn events` |
| `%clans_war~score.Alex%` | то же значение в PlaceholderAPI |

Короткие имена внутри своего плагина: в конфиге `pnclans` можно писать `{score}` вместо `{pnclans/war:score}`.

## Читать и подставлять из кода

```kotlin
val placeholders = module.placeholders                                   // PlaceholderService
placeholders.resolve("{pnclans/war:score.top.1.name}", playerId)         // CompletionStage<Any?>
placeholders.render("Лидер {pnclans/war:score.top.1.name}, баланс %vault_eco_balance%", playerId)
placeholders.resolve("{pnlibrary/main:version}")                         // встроенный
```

Проверить руками: `/pn placeholders test pnclans/war:score.top.3`.
