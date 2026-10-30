# Что библиотека хранит на диске

Всё лежит в папке данных pnLibrary (`plugins/pnLibrary`). Файлы нужны только для удобства: если файла нет или он повреждён, библиотека стартует как обычно и начинает записывать заново.

| Файл или папка | Что хранит | Формат | Как пишется |
|---|---|---|---|
| `config.yml` | настройки библиотеки | YAML | пользователь, при старте читается |
| `downloads.yml` | общие настройки скачивания | YAML | пользователь |
| `task-history.tsv` | завершённые задачи плагинов | таблица (`TsvRecords`), версия 3 | не чаще раза в 30 секунд и при остановке |
| `event-history.tsv` | вызовы событий по дням и часам | таблица со строкой версии | не чаще раза в минуту и при остановке |
| `event-history.csv`, `event-history.json` | выгрузка истории событий | CSV, JSON | только по кнопке выгрузки в меню или команде, не читается обратно |
| `journal/*.pnj` | журнал сервера по уровням и дням | бинарный | по событию, старые удаляются |
| `observability/observations.jsonl` | наблюдения за работой плагинов | JSON по строке | по событию |
| `diagnostics/history/*.pndlog` | журнал сессии для отчёта поддержки | бинарный | в течение сессии |
| `updates/` | каталог релизов, события обновлений, резервные копии | JSON и сами файлы | модуль обновлений |

## Единый формат таблиц

Таблицы с записями (история задач, дальше и история событий) пишет один класс `TsvRecords` из `modules/common`:

- первая строка называет формат и версию, например `#pnlibrary-task-history 3`;
- одна запись на строку, поля через табуляцию, табуляция, перенос строки и обратная косая внутри поля экранируются;
- файл заменяется целиком и атомарно, поэтому при сбое остаётся прежний;
- нечитаемая строка пропускается, отсутствующий файл читается как пустой.

Новая версия формата должна читать старые строки: так сделана история задач (версии 1, 2 и 3).

## Что дальше

- перевести `event-history.tsv` на `TsvRecords`;
- общий лимит размера и очистка старого для журнала, наблюдений и истории сессий;
- одна страница в меню «Данные библиотеки»: что и сколько занимает, кнопка очистки каждой части.

## link.key

Shared key of the link between a proxy and its game servers (`pnlibrary:link` plugin message channel). The proxy creates the file in its pnLibrary folder; copy it to the same folder on each game server and restart. A server reports its pnLibrary version, players and plugin update states every 30 seconds while a player is online; the proxy shows them in `/pn servers`. Reports are signed (HMAC-SHA256), so messages sent by players are ignored. The link only reports: nothing is run on a server.

The proxy also sends every game server a signed map of the network every 30 seconds (its own report and the latest report of each server). A game server shows it in `/pn gui` under «Сеть»: pick the proxy or another server and see its plugins and update states. This is read-only.

### Controlling another server

From `/pn servers <name> check | update <plugin> | auto <plugin> on|off` (or the buttons in the «Сеть» menu) a server can ask another one to look for updates, download a plugin update or switch automatic download. The request is signed with `link.key`, goes through the proxy and is answered the same way. The person asking needs the matching permission on the server they ask from (`pnlibrary.updates.view`, `.download`, `.settings`); the receiving server trusts anyone who holds the key, so keep `link.key` private. Nothing else can be sent: no commands, no files. A request id is accepted once.

## groups.tsv

Groups of servers on the proxy, next to `link.key`: which servers belong to a group and which plugins each of them should have. Managed with `/pn groups` (create, delete, add-server, remove-server, add-plugin, remove-plugin); `/pn groups <group>` shows which servers lack a plugin or can update it, and `check` / `update` send the matching requests to all servers of the group. The file may be edited by hand: one record per line, `group<TAB>group|server|plugin<TAB>value`.
