rootProject.name = "pnLibrary"

include(
    ":modules:common",
    ":modules:api",
    ":modules:core",
    ":modules:runtime-spi",
    ":modules:features:update",
    ":modules:features:minecraft-localization",
    ":modules:internal:bstats",
    ":modules:bootstrap:bukkit",
    ":modules:design:console",
    ":modules:compatibility:bukkit",
    ":modules:remote-check:bukkit",
    ":platforms:bukkit:api",
    ":platforms:bukkit:runtime",
    ":platforms:bungee:api",
    ":platforms:bungee:runtime",
    ":platforms:velocity:api",
    ":platforms:velocity:runtime",
    ":distribution",
    ":examples:demo-bukkit",
)
