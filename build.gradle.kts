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
            "bstats",
            "core",
            "distribution",
            "runtime",
            "runtime-spi",
            "update",
        ),
    )
}

repositories {
    mavenCentral()
}

// ── Convention for every subproject ─────────────────────────────────────────
subprojects {
    val moduleNamespace = path.substringBeforeLast(':').trim(':').replace(':', '.')
    group = "io.github.pnfolder" + moduleNamespace.takeIf(String::isNotEmpty)?.let { ".$it" }.orEmpty()
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
    dokka(project(":modules:common"))
    dokka(project(":modules:api"))
    dokka(project(":modules:runtime-spi"))
    dokka(project(":modules:core"))
    dokka(project(":modules:features:update"))
    dokka(project(":modules:features:minecraft-localization"))
    dokka(project(":platforms:bukkit:api"))
    dokka(project(":platforms:bungee:api"))
    dokka(project(":platforms:velocity:api"))
    dokka(project(":platforms:bukkit:runtime"))
    dokka(project(":platforms:bungee:runtime"))
    dokka(project(":platforms:velocity:runtime"))
}
