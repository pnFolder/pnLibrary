# Changelog

## 2.0.0-beta.6

- Added a synchronous, owner-scoped, platform-independent event bus with extensible numeric priorities.
- Reduced platform identity to the three supported `PlatformType` API families; fork names are runtime metadata.
- Added a high-level `PnLibraryRuntimeHost` shared by all native entry points.
- Removed platform adapter casts to the internal `PnLibraryImpl` implementation.
- Centralized `/pndebug` parsing, cooldown, execution, and reply dispatch.
- Added consistent pnLibrary startup and shutdown message boxes.
- Standardized Kotlin API and architecture documentation in English.
- Replaced wildcard imports and clarified ownership between API, core, and adapters.

## 2.0.0-beta.5

- Реализован сбор ограниченного и отредактированного журнала для `--logs`.
- `/pndebug all --config` теперь включает конфигурации всех плагинов и их владельцев.
- Закрыт выход из разрешённой папки через симлинк промежуточного каталога.
- Добавлен проверяемый `plugins/pnLibrary/config.yml` для настроек runtime.
- Сетевой сбой загрузки больше не уничтожает готовый локальный отчёт.
- Updater получил управляемый lifecycle и единое корректное сравнение SemVer.
- Folia использует одну reflection-based реализацию и объявлена в `plugin.yml`.
- Исправлена отмена отложенного callback в `asyncThen`.
- Code-first YAML создаёт отдельные резервные копии и хранит пять последних.
- Удалены неиспользуемые дубли API, пустой database router и старый слой логирования.
- Версия проекта перенесена в `gradle.properties`; сборка больше не привязана к JDK 26.
- Добавлены CI и дополнительные regression-тесты.
