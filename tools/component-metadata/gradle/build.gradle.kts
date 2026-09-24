plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnlibrary-component-metadata-gradle" }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
    plugins {
        create("pnComponentMetadata") {
            id = "ru.privatenull.pnlibrary.component-metadata"
            implementationClass = "ru.privatenull.pnlibrary.metadata.gradle.PnComponentMetadataPlugin"
            displayName = "pnLibrary component metadata"
            description = "Embeds pnLibrary component identity and API compatibility into a JAR"
        }
    }
}

dependencies {
    implementation(project(":tools:component-metadata:core"))
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-component-metadata-gradle", project.version.toString())
    pom {
        name.set("pnLibrary Component Metadata Gradle Plugin")
        description.set("Gradle plugin that embeds pnLibrary component metadata into JAR files.")
        inceptionYear.set("2026")
        url.set("https://github.com/pnFolder/pnLibrary")
        licenses { license { name.set("Apache License, Version 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0.txt") } }
        developers { developer { id.set("pnFolder"); name.set("pnFolder"); url.set("https://github.com/pnFolder") } }
        scm {
            url.set("https://github.com/pnFolder/pnLibrary")
            connection.set("scm:git:git://github.com/pnFolder/pnLibrary.git")
            developerConnection.set("scm:git:ssh://git@github.com/pnFolder/pnLibrary.git")
        }
    }
}
