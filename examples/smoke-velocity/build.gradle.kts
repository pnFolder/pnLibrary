import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins { alias(libs.plugins.kotlin.jvm); java }

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }
tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
tasks.withType<JavaCompile>().configureEach { options.release = 17 }
tasks.matching { it.name == "compileJava" || it.name == "compileKotlin" }.configureEach {
    dependsOn(":distribution:copyDeveloperArtifacts")
}

val distribution = files(project(":distribution").layout.buildDirectory.file(
    "libs/pnLibrary-${project.version}-velocity-java17.jar",
)).builtBy(":distribution:shadowVelocity")

dependencies {
    compileOnly(distribution)
    compileOnly(libs.velocity.api)
}
