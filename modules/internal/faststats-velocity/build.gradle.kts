import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-faststats-velocity" }

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }
afterEvaluate {
    tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_21) }
}
tasks.withType<JavaCompile>().configureEach { options.release = 21 }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:internal:faststats-core"))
    implementation(project(":modules:runtime-spi"))
    implementation(libs.faststats.velocity) { isTransitive = false }
    // All platform adapters share the same Java 8-compatible SDK core.
    implementation(libs.faststats.core.j8)
    compileOnly(libs.velocity.api)
}

java { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
