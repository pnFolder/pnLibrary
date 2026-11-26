plugins {
    `java-library`
}

base { archivesName = "${rootProject.property("pnlibrary.name")}-spi" }

dependencies {
    api(project(":modules:api"))
    compileOnly(libs.jetbrains.annotations)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

