import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    jacoco
    alias(libs.plugins.kotlin.jvm) apply false
    id("com.diffplug.spotless") version "8.10.4"
    alias(libs.plugins.shadow)     apply false
    alias(libs.plugins.dokka)
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.2"
}

// Source style is enforced by ktlint with only the rules named in .editorconfig, and runs as part of `check`.
// `./gradlew spotlessApply` fixes what can be fixed automatically (imports, whitespace, blank lines).
spotless {
    kotlin {
        target("modules/**/*.kt", "platforms/**/*.kt", "examples/**/*.kt")
        targetExclude("**/build/**")
        ktlint()
    }
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
            "policy-kotlin",
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
        // Coverage data is collected by every test run; reports are only built on request (see coverageReport).
        pluginManager.apply("jacoco")
    }
}

// One report over every module's tests: `./gradlew test coverageReport`, then open
// build/reports/coverage/html/index.html. It shows which production code no test executes.
val coverageReport = tasks.register<JacocoReport>("coverageReport") {
    group = "verification"
    description = "Aggregated JaCoCo coverage report for all modules (run the tests first)"
    reports {
        html.required.set(true)
        xml.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/coverage/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/coverage/coverage.xml"))
    }
}
subprojects {
    pluginManager.withPlugin("jacoco") {
        coverageReport.configure {
            val main = this@subprojects.extensions.getByType<SourceSetContainer>().getByName("main")
            sourceDirectories.from(main.allSource.srcDirs)
            classDirectories.from(main.output.classesDirs.asFileTree.matching { exclude("org/bstats/**") })
            // Depends on the module's `test` task, so the report always reflects a fresh run.
            executionData(this@subprojects.tasks.named("test").get())
        }
    }
}

// Public contracts must never pull implementation modules onto their compile classpath.
val publicModulesFile = file("gradle/public-modules.properties")
val publicModuleDependencies = publicModulesFile.readLines().map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }.associate { line ->
        val path = ":" + line.substringBefore('=').replace('/', ':')
        path to line.substringAfter('=').split(',').filter(String::isNotBlank)
            .map { ":" + it.trim().replace('/', ':') }.toSet()
    }

val verifyPublicModuleBoundaries = tasks.register("verifyPublicModuleBoundaries") {
    group = "verification"
    description = "Rejects implementation-module dependencies in public API artifacts"
    inputs.file(publicModulesFile)
    doLast {
        val signatureModules = subprojects.filter { module ->
            module.file("api").listFiles()?.any { it.extension == "api" } == true
        }.map { it.path }.toSet() - ":modules:runtime-spi"
        check(signatureModules == publicModuleDependencies.keys) {
            "Public module registry and signature modules differ: " +
                "${(signatureModules - publicModuleDependencies.keys) + (publicModuleDependencies.keys - signatureModules)}"
        }
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
        publicModuleDependencies.keys.map { "$it:apiCheck" },
        ":modules:runtime-spi:apiCheck",
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
