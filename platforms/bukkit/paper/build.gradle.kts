// pnlibrary-bukkit-paper — registers portable commands through Paper's Brigadier command API (Java 21 bytecode).
// The Bukkit runtime loads it reflectively only on a Paper server that has the API; every other server
// keeps the classic command map, so this module is never required.

plugins {
    `java-library`
}

base { archivesName = "${rootProject.property("pnlibrary.name")}-bukkit-paper" }

dependencies {
    compileOnly(project(":modules:api"))
    compileOnly(project(":modules:spi"))
    compileOnly(libs.paper.api)

    testImplementation(project(":modules:api"))
    testImplementation(project(":modules:spi"))
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
