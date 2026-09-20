import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnLibrary-minecraft-localization" }

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 } }
tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    api(project(":modules:common"))
    api(project(":platforms:bukkit:api"))
    api(libs.kotlin.stdlib)
    implementation(libs.gson)
    compileOnly(libs.spigot.api.v18)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spigot.api.v18)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-minecraft-localization", project.version.toString())
    pom {
        name.set("pnLibrary Minecraft Localization")
        description.set("On-demand official Minecraft translations and reverse lookup.")
        inceptionYear.set("2026")
        url.set("https://github.com/pnFolder/pnLibrary")
        licenses { license {
            name.set(providers.gradleProperty("POM_LICENSE_NAME"))
            url.set(providers.gradleProperty("POM_LICENSE_URL"))
            distribution.set(providers.gradleProperty("POM_LICENSE_DIST").orElse("repo"))
        } }
        developers { developer { id.set("pnFolder"); name.set("pnFolder"); url.set("https://github.com/pnFolder") } }
        scm {
            url.set("https://github.com/pnFolder/pnLibrary")
            connection.set("scm:git:git://github.com/pnFolder/pnLibrary.git")
            developerConnection.set("scm:git:ssh://git@github.com/pnFolder/pnLibrary.git")
        }
    }
}
