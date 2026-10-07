import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-faststats-bungee" }

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 } }
tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:internal:faststats-core"))
    implementation(project(":modules:internal:bstats"))
    implementation(libs.faststats.bungeecord.j8)
    compileOnly(libs.bungeecord.api)
}

java { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
