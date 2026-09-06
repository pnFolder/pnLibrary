import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow)     apply false
}

// ── Shared version catalogue ─────────────────────────────────────────────────
val pnVersion = "2.0.0-beta.3"

// ── Convention for every subproject ─────────────────────────────────────────
subprojects {
    group   = "ru.privatenull"
    version = pnVersion

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")  { name = "papermc" }
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "spigot" }
        maven("https://repo.md-5.net/content/repositories/snapshots/")          { name = "bungeecord" }
        maven("https://jitpack.io")                                              { name = "jitpack" }
    }

    // Wire up Kotlin source sets for all subprojects that apply kotlin("jvm")
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(26)
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
        // Enforce UTF-8 for any Java sources that may exist temporarily
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
