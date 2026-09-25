# pnLibrary Currency Modules

Platform-neutral currency registry, managed providers, transaction model, and file/JDBC storage.

The feature is split into `currency-api` and `currency-runtime`. It contains no Bukkit, BungeeCord,
Velocity, Vault, or PlayerPoints classes. Platform
integrations remain in their platform runtime. In particular, the Bukkit `/pncurrency` command and
reflective Vault/PlayerPoints adapters live under `platforms/bukkit/runtime`.

Consumers compile against `pnlibrary-currency-api`. The runtime implementation is published as
`pnlibrary-currency` and is installed into the common service registry by `pnLibrary-core`.
Implementation classes such as the registry and storage backends remain private to the runtime
module.
