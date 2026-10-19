// pnlibrary-velocity — Velocity 3.x adapter
// Velocity 3 requires Java 17+; that is a platform requirement, not a preference.

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-velocity" }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:runtime-spi"))
    implementation(project(":modules:core"))
    implementation(project(":platforms:velocity:api"))
    implementation(libs.kotlin.stdlib)
    // The adapter is instantiated only after the factory checks for Java 21. The bridge is built for Java 21,
    // so this Java 17 module asks for that variant explicitly instead of failing Gradle's JVM-version matching.
    compileOnly(project(":modules:internal:faststats-velocity")) {
        attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21) }
    }
    compileOnly(libs.velocity.api)
    // Provided by the proxy; used only to observe plugin warnings and errors.
    compileOnly(libs.log4j.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.velocity.api)
    testImplementation(libs.log4j.core)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
}

// The Velocity @Plugin annotation needs literal authors, so fail the build when it drifts from gradle.properties.
val verifyVelocityAuthors by tasks.registering {
    val source = file("src/main/kotlin/org/pnfolder/pnlibrary/velocity/PnLibraryVelocityPlugin.kt")
    val expected = rootProject.property("pnlibrary.authors").toString()
        .split(",").map(String::trim).filter(String::isNotEmpty)
    inputs.file(source)
    inputs.property("expected", expected)
    doLast {
        val declared = Regex("""authors\s*=\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(source.readText())?.groupValues?.get(1)
            ?.split(",")?.map { it.trim().trim('"') }?.filter(String::isNotEmpty)
        check(declared == expected) {
            "Velocity @Plugin authors $declared do not match gradle.properties $expected"
        }
    }
}
tasks.named("check") { dependsOn(verifyVelocityAuthors) }
