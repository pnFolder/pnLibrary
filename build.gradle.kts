import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow)     apply false
    alias(libs.plugins.dokka)
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.2"
}

apiValidation {
    ignoredProjects.addAll(
        listOf(
            "core",
            "distribution",
            "runtime",
            "faststats",
            "paper",
            "currency-runtime",
            "config-runtime",
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
        maven("https://libraries.minecraft.net")                                { name = "minecraft" }
        maven("https://repo.md-5.net/content/repositories/snapshots/")          { name = "bungeecord" }
        maven("https://repo.extendedclip.com/releases/")                        { name = "placeholderapi" }
        maven("https://repo.faststats.dev/releases")                            { name = "faststats" }
    }

    // Bytecode and API level: Java 8 unless a module sets `pn.java.release` in its gradle.properties
    // (Velocity modules need 17, the FastStats Velocity bridge 21).
    val javaRelease = (findProperty("pn.java.release") as String?)?.toInt() ?: 8

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(javaRelease)
        options.compilerArgs.add("-Xlint:-options")
    }
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.toVersion(javaRelease)
            targetCompatibility = JavaVersion.toVersion(javaRelease)
        }
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
                jvmTarget.set(JvmTarget.fromTarget(if (javaRelease == 8) "1.8" else javaRelease.toString()))
                freeCompilerArgs.addAll(
                    "-Xjvm-default=all",
                    "-opt-in=kotlin.RequiresOptIn"
                )
            }
        }
        // Bytecode target alone does not prevent calls to newer JDK APIs.
        tasks.named<KotlinCompile>("compileKotlin") {
            compilerOptions.freeCompilerArgs.add(
                compilerOptions.jvmTarget.map { "-Xjdk-release=${it.target.removePrefix("1.")}" },
            )
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

// Public contracts must never pull implementation modules onto their compile classpath.
val publicModuleDependencies = mapOf(
    ":modules:common" to emptySet<String>(),
    ":modules:api" to setOf(":modules:common", ":modules:features:config-api"),
    ":modules:features:currency-api" to setOf(":modules:api"),
    ":platforms:bukkit:api" to setOf(":modules:api", ":modules:common"),
    ":platforms:bungee:api" to setOf(":modules:api"),
    ":platforms:velocity:api" to setOf(":modules:api"),
    ":modules:features:minecraft-localization" to setOf(":modules:common", ":platforms:bukkit:api"),
)

val verifyPublicModuleBoundaries = tasks.register("verifyPublicModuleBoundaries") {
    group = "verification"
    description = "Rejects implementation-module dependencies in public API artifacts"
    doLast {
        publicModuleDependencies.forEach { (modulePath, allowed) ->
            val dependencies = project(modulePath).configurations.getByName("compileClasspath")
                .allDependencies.withType<ProjectDependency>().map { it.path }.toSet()
            val unexpected = dependencies - allowed
            check(unexpected.isEmpty()) {
                "$modulePath has forbidden implementation dependencies: ${unexpected.sorted()}"
            }
        }
    }
}

// Public API changes must be reviewed through the same verification gate as tests: every module with a golden
// signature dump is checked, so consumers and platform adapters are protected from accidental binary breakage.
tasks.named("check") {
    dependsOn(
        verifyPublicModuleBoundaries,
        ":modules:common:apiCheck",
        ":modules:api:apiCheck",
        ":modules:runtime-spi:apiCheck",
        ":modules:features:currency-api:apiCheck",
        ":modules:features:minecraft-localization:apiCheck",
        ":platforms:bukkit:api:apiCheck",
        ":platforms:bungee:api:apiCheck",
        ":platforms:velocity:api:apiCheck",
    )
}

dependencies {
    dokka(project(":modules:common"))
    dokka(project(":modules:api"))
    dokka(project(":modules:runtime-spi"))
    dokka(project(":modules:core"))
    dokka(project(":modules:features:currency-api"))
    dokka(project(":modules:features:currency-runtime"))
    dokka(project(":modules:features:minecraft-localization"))
    dokka(project(":modules:bootstrap"))
    dokka(project(":platforms:bukkit:bootstrap"))
    dokka(project(":platforms:bungee:bootstrap"))
    dokka(project(":platforms:velocity:bootstrap"))
    dokka(project(":modules:console"))
    dokka(project(":platforms:bukkit:api"))
    dokka(project(":platforms:bungee:api"))
    dokka(project(":platforms:velocity:api"))
    dokka(project(":platforms:bukkit:runtime"))
    dokka(project(":platforms:bungee:runtime"))
    dokka(project(":platforms:velocity:runtime"))
}

// Builds the local acceptance kit and copies only the current-version JARs to the Paper test server named by
// the `pnlibrary.installDir` Gradle property (for example in ~/.gradle/gradle.properties). Existing unrelated
// plugin files are left untouched.
tasks.register<Copy>("installAcceptanceToPaper") {
    group = "distribution"
    description = "Builds pnLibrary and acceptance, then installs them into the local Paper plugins directory"
    dependsOn(":examples:acceptance-bukkit:assembleAcceptanceKit")
    val releaseVersion = version.toString()
    from(layout.buildDirectory.dir("acceptance-bukkit")) {
        include("pnLibrary-${releaseVersion}-bukkit-java8.jar")
        include("pnLibrary-acceptance-${releaseVersion}-bukkit-java8.jar")
    }
    val installDir = providers.gradleProperty("pnlibrary.installDir")
    into(installDir.map { file(it) }.orElse(provider {
        throw GradleException("Set the pnlibrary.installDir Gradle property to the plugins directory of a test server")
    }))
}
