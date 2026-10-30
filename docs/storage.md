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
