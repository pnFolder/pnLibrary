rootProject.name = "pnLibrary"

// ── New modular Kotlin architecture ─────────────────────────────────────────
include("pnlibrary-api")
include("pnlibrary-runtime-spi")
include("pnlibrary-bstats-base")
include("pnlibrary-core")
include("pnlibrary-feature-update")
include("pnlibrary-bukkit-api")
include("pnlibrary-bungee-api")
include("pnlibrary-velocity-api")
include("pnlibrary-bukkit")
include("pnlibrary-bungee")
include("pnlibrary-velocity")

project(":pnlibrary-bukkit-api").projectDir = file("platforms/bukkit/api")
project(":pnlibrary-bukkit").projectDir = file("platforms/bukkit/runtime")
project(":pnlibrary-bungee-api").projectDir = file("platforms/bungee/api")
project(":pnlibrary-bungee").projectDir = file("platforms/bungee/runtime")
project(":pnlibrary-velocity-api").projectDir = file("platforms/velocity/api")
project(":pnlibrary-velocity").projectDir = file("platforms/velocity/runtime")

include("pnlibrary-distribution")
