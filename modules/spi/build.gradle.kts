plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "${rootProject.property("pnlibrary.name")}-spi" }

dependencies {
    api(project(":modules:api"))
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

