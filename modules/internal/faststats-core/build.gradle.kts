import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-faststats-core" }
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 } }
tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:internal:bstats"))
    implementation(libs.faststats.core.j8)
}

java { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
