# pnLibrary Currency Feature

Platform-neutral currency registry, managed providers, transaction model, and file/JDBC storage.

This module contains no Bukkit, BungeeCord, Velocity, Vault, or PlayerPoints classes. Platform
integrations remain in their platform runtime. In particular, the Bukkit `/pncurrency` command and
reflective Vault/PlayerPoints adapters live under `platforms/bukkit/runtime`.

`pnLibrary-core` only owns the feature lifecycle through `CurrencyFeature`; implementation classes
such as the registry and storage backends are private to this module.
