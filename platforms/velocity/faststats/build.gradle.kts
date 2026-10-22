plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "${rootProject.property("pnlibrary.name")}-faststats-velocity" }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:core"))
    implementation(project(":modules:runtime-spi"))
    implementation(libs.faststats.velocity) { isTransitive = false }
    // All platform adapters share the same Java 8-compatible SDK core.
    implementation(libs.faststats.core.j8)
    compileOnly(libs.velocity.api)
}

