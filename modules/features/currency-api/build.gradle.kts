import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnLibrary-currency-api" }

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 } }
tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    api(project(":modules:api"))
    api(libs.kotlin.stdlib)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-currency-api", project.version.toString())
    pom {
        name.set("pnLibrary Currency API")
        description.set("Optional public currency contracts for pnLibrary consumers.")
        inceptionYear.set("2026")
        url.set("https://github.com/pnFolder/pnLibrary")
        licenses {
            license {
                name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                url.set(providers.gradleProperty("POM_LICENSE_URL"))
                distribution.set(providers.gradleProperty("POM_LICENSE_DIST").orElse("repo"))
            }
        }
        developers { developer { id.set("pnFolder"); name.set("pnFolder"); url.set("https://github.com/pnFolder") } }
        scm {
            url.set("https://github.com/pnFolder/pnLibrary")
            connection.set("scm:git:git://github.com/pnFolder/pnLibrary.git")
            developerConnection.set("scm:git:ssh://git@github.com/pnFolder/pnLibrary.git")
        }
    }
}
