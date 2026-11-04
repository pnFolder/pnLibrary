# Плейсхолдеры: полный пример

Набросок кода для обсуждения, не компилируется (API ещё не написан). Один файл показывает весь функционал.
План и правила: [placeholders-plan.md](placeholders-plan.md).

```kotlin
package com.example.clans

import org.bukkit.plugin.java.JavaPlugin

// Данные, из которых можно сгенерировать ветки (см. from<Clan> ниже)
data class Clan(val name: String, val tag: String, val members: Int, val bank: Double, val leader: String)

class ClansPlugin : JavaPlugin() {                                   // id плагина: "pnclans"
    private val clans = ClanStorage()
    private val shop = ShopApi()

    override fun onEnable() = pnPlugin(this) {                       // всё внутри закрывается само при выключении

        // ───────────────────────── Настройки форматов (админ меняет в конфиге библиотеки) ─────────────────────────
        // Хранятся аннотациями в коде, не в текстовом файле. Здесь автор плагина задаёт значения по умолчанию.
        formatSettings {
            number { grouping(" "); decimals(2) }                    // 1 234 567,89
            money  { suffix(" монет"); decimals(0) }                 // 12 500 монет
        }

        module("war") {                                              // без module(...) всё было бы в модуле "main"

            // ───────────────────────── 1. Свои форматы ─────────────────────────
            // Видны как {…|war/имя}, внутри плагина можно {…|имя}
            formats {
                format<Int>("rank") { "#$it" }                                          // 3         -> #3
                format<Int>("stars") { n -> "★".repeat(n.coerceIn(0, 5)) }              // 3         -> ★★★
                format<Int>("pad0", arg = int) { n, width -> "%0${width}d".format(n) }  // 42, (5)   -> 00042
                format<String>("brackets") { "[$it]" }                                  // WOLF      -> [WOLF]
                format<Any>("safe") { it?.toString() ?: "—" }                           // любой тип
            }

            // ───────────────────────── 2. Плейсхолдеры ─────────────────────────
            // papi = "clans": вся группа публикуется в PlaceholderAPI как %clans_война~…%
            placeholders(papi = "clans") {

                format("number")                                     // формат по умолчанию для всей группы
                access { allowAll() }                                // по умолчанию и так так: открыто всем плагинам

                // ── Обычная ветка с продолжением (дерево, как команды) ──
                int("score") {                                       // {pnclans/war:score}                  %clans_war~score%
                    value { clans.scoreOf(player) }                  // player: для кого рендерим, может быть null

                    player("who") {                                  // {pnclans/war:score.Alex}             ник или UUID
                        value { clans.scoreOf(arg) }                 // arg: объект игрока

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

                // ── Форматы: четыре уровня, каждый следующий сильнее ──
                //   нет формата -> формат группы -> формат ветки -> формат в тексте ({…|…})
                int("id")      { format(none);    value { clans.idOf(player) } }  // без формата: {…:id} -> 1234 (группа сказала бы 1 234)
                number("bank") {
                    format("money")
                    value { clans.bank(clans.clanOf(player)) }
                    // Запись значения извне: менять может только pnshop
                    update(allowOnly("pnshop")) { text -> clans.setBank(clans.clanOf(player), text.toDouble()) }
                }
                //   {pnclans/war:bank}            -> 12 500 монет
                //   {pnclans/war:bank|number}     -> 12 500
                //   {pnclans/war:bank|round(0)}   -> 12500
                //   {pnclans/war:bank|raw}        -> 12500.0   (любой формат по умолчанию отключён)
                //   placeholders.update("{pnclans/war:bank}", "700")  — вызовет только pnshop

                // ── Только для {…}, наружу в PlaceholderAPI не публикуется ──
                int("debug") { inside(); value { clans.debugCounter() } }

                // ── Доступ: читать могут только эти плагины ──
                int("secret") {
                    access { allowOnly("pnshop", "pnbank") }         // или deny("pnspy"), или denyAll()
                    value { clans.secretOf(player) }
                }

                // ───────────────────────── 3. Генерация из данных ─────────────────────────
                // Каждое свойство Clan становится веткой, тип определяется сам:
                from<Clan>("clan") { clans.clanOf(player) }
                //   {pnclans/war:clan.name}  {pnclans/war:clan.tag}  {pnclans/war:clan.members}
                //   {pnclans/war:clan.bank}  {pnclans/war:clan.leader}
                //   %clans_war~clan.name%    и т.д.

                // С выбором игрока в начале пути, лишнее убрано, имя изменено
                from<Clan>("clanof", by = player("who")) { clans.clanOf(arg) }
                    .only(Clan::name, Clan::tag, Clan::members)
                    .rename(Clan::members, "size")
                //   {pnclans/war:clanof.Alex.name}  {pnclans/war:clanof.Alex.size}

                // Из таблицы значений: ветка на каждый ключ
                table("price", keys = { shop.items() }) { item -> shop.priceOf(item) }
                //   {pnclans/war:price.sword}  {pnclans/war:price.apple}

                // Из списка с номером места
                list("top5", size = { 5 }) { place -> clans.topName(place) }
                //   {pnclans/war:top5.1} … {pnclans/war:top5.5}
            }

            // ───────────────────────── 4. Вторая группа с другим префиксом ─────────────────────────
            placeholders(papi = "cl") {
                text("tag") { value { clans.clanOf(player)?.tag }; format("brackets") }
                //   {pnclans/war:tag}   ->  [WOLF]     %cl_war~tag%
            }

            // ───────────────────────── 5. Группа без публикации ─────────────────────────
            placeholders {                                           // нет papi -> только для {…}
                int("internal") { value { clans.counter() } }
                //   {pnclans/war:internal}
            }
        }

        // ───────────────────────── 6. Текст из конфига: короткие имена внутри своего плагина ─────────────────────────
        // config.yml плагина pnclans:
        //   top-message: "Лидер: {score.top.1.name|upper}, у тебя {score|stars} ({score|rank}), банк {bank}"
        // Внутри плагина "pnclans" {score} = {pnclans/war:score}
        command("clans") {
            executes {
                reply(render(config.getString("top-message")))       // 1) наши {…}  2) остаток %…% в PlaceholderAPI
                reply(render("Ты: {score}, баланс: %vault_eco_balance%"))   // смешанный текст
                reply(render("Привет, {name}!", values = mapOf("name" to sender.name)))   // значения от вызывающего
            }
        }

        // ───────────────────────── 7. Чтение чужого из кода ─────────────────────────
        every(1.minutes) {
            val vault = placeholders.resolve("%vault_eco_balance%", player)      // через PlaceholderAPI
            val mine = placeholders.resolve("{pnclans/war:score.top.1.name}")    // наш, по полному адресу
            val shop = placeholders.resolve("{pnshop/main:price.sword}")         // чужой плагин библиотеки
            val lib = placeholders.resolve("{pnlibrary/main:version}")           // встроенный
        }
    }
}
```

## Что получает игрок и админ

| Где | Текст | Результат |
|---|---|---|
| Конфиг `pnclans` | `Лидер: {score.top.1.name\|upper}` | `Лидер: ALEX` |
| Конфиг другого плагина | `Очки: {pnclans/war:score.Alex.3}` | `Очки: 1 234` |
| Скорборд | `%clans_war~score%` | `1 234` |
| Скорборд, группа `cl` | `%cl_war~tag%` | `[WOLF]` |
| Сгенерировано | `{pnclans/war:clan.members}` | `12` |
| Таблица | `{pnclans/war:price.sword}` | `50 монет` |
| Смешанный текст | `Ты: {pnclans/war:score}, баланс: %vault_eco_balance%` | `Ты: 1 234, баланс: 500` |
| Неизвестное | `{pnclans/war:nothing}` | остаётся как есть, причина в `/pn events` |

## Что будет рядом

- `/pn placeholders`, `/pn placeholders test <адрес> [игрок]`, страница меню.
- Встроенные `pnlibrary/main:version`, `plugins`, `uptime`, `stats.errors`.
- Старое API плейсхолдеров остаётся рабочим через адаптеры (`@Deprecated`).
