rootProject.name = "pnLibrary"

include(
    // Public contracts and shared code.
    ":modules:common",
    ":modules:api",
    ":modules:runtime-spi",
    ":modules:console",
    // Runtime.
    ":modules:core",
    ":modules:updates",
    ":modules:bootstrap",
    // Features.
    ":modules:features:config-api",
    ":modules:features:config-runtime",
    ":modules:features:currency-api",
    ":modules:features:currency-runtime",
    ":modules:features:minecraft-localization-api",
    // Platforms.
    ":platforms:loader",
    ":platforms:bukkit:api",
    ":platforms:bukkit:runtime",
    ":platforms:bukkit:paper",
    ":platforms:bukkit:localization",
    ":platforms:bukkit:bootstrap",
    ":platforms:bungee:api",
    ":platforms:bungee:runtime",
    ":platforms:bungee:bootstrap",
    ":platforms:velocity:api",
    ":platforms:velocity:runtime",
    ":platforms:velocity:bootstrap",
    ":platforms:velocity:faststats",
    // Packaging and examples.
    ":distribution",
    ":examples:acceptance-bukkit",
    ":examples:bootstrap-bukkit",
)
