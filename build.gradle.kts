import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow)     apply false
    alias(libs.plugins.dokka)
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.2"
}

apiValidation {
    ignoredProjects.addAll(
        listOf(
            "pnlibrary-bstats-base",
            "pnlibrary-bukkit",
            "pnlibrary-bungee",
            "pnlibrary-core",
            "pnlibrary-distribution",
            "pnlibrary-feature-update",
            "pnlibrary-runtime-spi",
            "pnlibrary-velocity",
        ),
    )
}

repositories {
    mavenCentral()
}

// ── Convention for every subproject ─────────────────────────────────────────
subprojects {
    group   = "io.github.pnfolder"
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")  { name = "papermc" }
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "spigot" }
        maven("https://repo.md-5.net/content/repositories/snapshots/")          { name = "bungeecord" }
        maven("https://repo.extendedclip.com/releases/")                        { name = "placeholderapi" }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:-options")
    }

    // Wire up Kotlin source sets for all subprojects that apply kotlin("jvm")
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        pluginManager.apply("org.jetbrains.dokka")

        extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
            dokkaSourceSets.configureEach {
                reportUndocumented.set(true)
                perPackageOption {
                    matchingRegex.set("org\\.bstats(?:\\..*)?")
                    suppress.set(true)
                }
            }
            dokkaPublications.configureEach {
                failOnWarning.set(true)
                suppressObviousFunctions.set(true)
            }
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    "-Xjvm-default=all",
                    "-opt-in=kotlin.RequiresOptIn"
                )
            }
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

dependencies {
    dokka(project(":pnlibrary-api"))
    dokka(project(":pnlibrary-runtime-spi"))
    dokka(project(":pnlibrary-core"))
    dokka(project(":pnlibrary-feature-update"))
    dokka(project(":pnlibrary-bukkit-api"))
    dokka(project(":pnlibrary-bungee-api"))
    dokka(project(":pnlibrary-velocity-api"))
    dokka(project(":pnlibrary-bukkit"))
    dokka(project(":pnlibrary-bungee"))
    dokka(project(":pnlibrary-velocity"))
}
