import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
    jacoco
    alias(libs.plugins.shadow)     apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.2"
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
        maven("https://repo.alessiodp.com/releases/")                          { name = "libby" }
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

    // The binary-compatibility-validator plugin only wires itself into Kotlin projects, and there is no Kotlin plugin
    // any more, so its tasks are registered here for every module that keeps a golden dump in `api/<name>.api`.
    // `apiDump` (rewrite the dump) and `apiCheck` (compare, part of `check`) behave as before.
    val dumpName = "$name.api"
    if (file("api/$dumpName").exists()) {
        pluginManager.withPlugin("java") {
            val bcvRuntime = configurations.create("bcvRuntime") {
                isCanBeConsumed = false
                // The metadata reader only has to parse class files, any 2.x release does.
                listOf("org.ow2.asm:asm:9.6", "org.ow2.asm:asm-tree:9.6", "org.jetbrains.kotlin:kotlin-metadata-jvm:2.1.21")
                    .forEach { this@subprojects.dependencies.add(name, it) }
            }
            val main = extensions.getByType<SourceSetContainer>().getByName("main")
            val apiBuild = tasks.register<KotlinApiBuildTask>("apiBuild") {
                inputClassesDirs.from(main.output.classesDirs)
                outputApiFile.set(layout.buildDirectory.file("api/$dumpName"))
                runtimeClasspath.from(bcvRuntime)
            }
            tasks.register<KotlinApiCompareTask>("apiCheck") {
                group = "verification"
                description = "Compares the public API with the golden dump in api/$dumpName"
                projectApiFile.set(layout.projectDirectory.file("api/$dumpName"))
                generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
            }
            tasks.register<Copy>("apiDump") {
                group = "other"
                description = "Rewrites api/$dumpName from the compiled classes"
                from(apiBuild.flatMap { it.outputApiFile })
                into(layout.projectDirectory.dir("api"))
            }
            tasks.named("check") { dependsOn("apiCheck") }
        }
    }

    pluginManager.withPlugin("java") {
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
        }.map { it.path }.toSet() - ":modules:spi"
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
        ":modules:spi:apiCheck",
    )
}

