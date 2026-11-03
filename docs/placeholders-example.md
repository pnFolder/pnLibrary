# Плейсхолдеры: полный пример

Набросок кода для обсуждения, не компилируется (API ещё не написан). Один файл показывает весь функционал:
плагин, модуль, дерево, аргументы, доступ, форматы, публикацию в PlaceholderAPI, чтение чужих и запись.
План и правила: [placeholders-plan.md](placeholders-plan.md).

```kotlin
package com.example.clans

import org.bukkit.plugin.java.JavaPlugin

class ClansPlugin : JavaPlugin() {                                   // id плагина: "pnclans"
    private val clans = ClanStorage()

    override fun onEnable() = pnPlugin(this) {                       // всё внутри закрывается само при выключении

        module("war") {                                              // без module(...) всё было бы в модуле "main"

            // 1. Свои форматы. Видны как {…|war/имя}, внутри плагина можно {…|rank}
            formats {
                format<Int>("rank") { "#$it" }                                    // 3        -> #3
                format<Int>("stars") { n -> "★".repeat(n.coerceIn(0, 5)) }        // 3        -> ★★★
                format<Int>("pad0", arg = int) { n, width -> "%0${width}d".format(n) }   // 42, (5)  -> 00042
                format<Any>("safe") { it?.toString() ?: "—" }                     // любой тип
            }

            // 2. Плейсхолдеры. papi = "clans" публикует всю группу в PlaceholderAPI под этим префиксом
            placeholders(papi = "clans") {

                access { allowAll() }                                // по умолчанию и так так; для примера
                format("number")                                     // формат по умолчанию для всей группы

                int("score") {                                       // {pnclans/war:score}           %clans_war~score%
                    value { clans.scoreOf(player) }                  // player: для кого рендерим, может быть null

                    player("who") {                                  // {pnclans/war:score.Alex}      %clans_war~score.Alex%
                        value { clans.scoreOf(arg) }                 // arg: объект игрока (ник или UUID)

                        int("kills") {                               // {pnclans/war:score.Alex.kills}
                            value { clans.killsOf(arg) }
                        }
                        text("clan") {                               // {pnclans/war:score.Alex.clan}
                            value { clans.clanOf(arg)?.name }
                            choice("field", "tag", "members") {      // {pnclans/war:score.Alex.clan.tag}
                                value { clans.clanField(clans.clanOf(argOf("who")), arg) }
                            }
                        }
                        param("season", int) {                       // {pnclans/war:score.Alex.3}
                            value { clans.scoreOf(argOf("who"), season = arg) }
                            int("kills") {                           // {pnclans/war:score.Alex.3.kills}
                                value { clans.killsOf(argOf("who"), season = argOf("season")) }
                            }
                        }
                    }

                    int("top") {                                     // {pnclans/war:score.top}
                        value { clans.topScore(1) }
                        param("place", int, range = 1..100) {        // {pnclans/war:score.top.3}
                            value { clans.topScore(arg) }
                            text("name") {                           // {pnclans/war:score.top.3.name}
                                value { clans.topName(argOf("place")) }
                            }
                        }
                    }
                }

                number("bank") {                                     // {pnclans/war:bank}            -> 12 500 (формат number по умолчанию)
                    format("money")                                  // перекрывает формат группы: 12 500 монет
                    value { clans.bank(clans.clanOf(player)) }

                    // Запись извне: {pnclans/war:bank} может менять только pnshop
                    update(allowOnly("pnshop")) { text -> clans.setBank(clans.clanOf(player), text.toDouble()) }
                }

                // Только для {…}, в PlaceholderAPI не публикуется
                int("debug") { inside(); value { clans.debugCounter() } }

                // Ограничение доступа на одной ветке: читать могут только эти плагины
                int("secret") {
                    access { allowOnly("pnshop", "pnbank") }         // или deny("pnspy"), или denyAll()
                    value { clans.secretOf(player) }
                }
            }

            // 3. Вторая группа с другим префиксом, например короткие значения для табов
            placeholders(papi = "cl") {
                text("tag") { value { clans.clanOf(player)?.tag } }  // {pnclans/war:tag}   %cl_war~tag%
            }
        }

        // 4. Текст из конфига плагина: свои плейсхолдеры можно писать коротко
        //    config.yml:  top-message: "Лидер: {score.top.1.name|upper}, у тебя {score|stars} ({score|rank})"
        //    (внутри плагина "pnclans" {score} = {pnclans/war:score})
        command("clans") {
            executes {
                val text = render(config.getString("top-message"))   // 1) наши {…}  2) остаток %…% в PlaceholderAPI
                reply(text)
            }
        }

        // 5. Чтение чужого прямо из кода
        every(1.minutes) {
            val vault = placeholders.resolve("%vault_eco_balance%", player)       // через PlaceholderAPI
            val mine = placeholders.resolve("{pnclans/war:score.top.1.name}")     // наш, по полному адресу
            val others = placeholders.resolve("{pnshop/main:prices.sword}")       // чужой плагин библиотеки
        }
    }
}
```

## Что получает игрок и админ

| Где | Текст | Результат |
|---|---|---|
| Конфиг `pnclans` | `Лидер: {score.top.1.name\|upper}` | `Лидер: ALEX` |
| Конфиг другого плагина | `Очки: {pnclans/war:score.Alex.3\|number}` | `Очки: 1 234` |
| Скорборд | `%clans_war~score%` | `1 234` |
| Скорборд (короткая группа) | `%cl_war~tag%` | `WOLF` |
| Смешанный текст | `Ты: {pnclans/war:score}, баланс: %vault_eco_balance%` | `Ты: 1 234, баланс: 500` |
| Неизвестное | `{pnclans/war:nothing}` | остаётся как есть, причина в `/pn events` |

## Чего нет в примере, но будет

- `/pn placeholders`, `/pn placeholders test <адрес> [игрок]`, страница меню.
- Встроенные `pnlibrary/main:version`, `pnlibrary/main:plugins`, `pnlibrary/main:uptime`, `pnlibrary/main:stats.errors`.
- Старое API плейсхолдеров остаётся рабочим через адаптеры (`@Deprecated`).
