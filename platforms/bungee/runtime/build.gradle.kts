// pnlibrary-bungee — BungeeCord / Waterfall adapter, JVM 8 bytecode

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-bungee" }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:runtime-spi"))
    implementation(project(":modules:core"))
    implementation(project(":platforms:bungee:api"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.faststats.bungeecord.j8)
    implementation(libs.adventure.legacy)
    compileOnly(libs.bungeecord.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.bungeecord.api)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
}

val identityProperties = mapOf(
    "version" to version.toString(),
    "name" to rootProject.property("pnlibrary.name").toString(),
    "author" to rootProject.property("pnlibrary.authors").toString()
        .split(",").map(String::trim).filter(String::isNotEmpty).joinToString(", "),
)
tasks.named<ProcessResources>("processResources") {
    inputs.properties(identityProperties)
    filesMatching("bungee.yml") {
        expand(identityProperties)
    }
}
