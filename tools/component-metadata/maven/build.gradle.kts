plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnlibrary-component-metadata-maven" }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":tools:component-metadata:core"))
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)
    testImplementation(libs.maven.plugin.api)
    testImplementation(libs.maven.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("pluginVersion", project.version.toString())
    filesMatching("META-INF/maven/plugin.xml") {
        filter { line -> line.replace("@VERSION@", project.version.toString()) }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-component-metadata-maven", project.version.toString())
    pom {
        name.set("pnLibrary Component Metadata Maven Plugin")
        description.set("Maven plugin that embeds pnLibrary component metadata into JAR files.")
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

publishing.publications.withType<MavenPublication>().configureEach {
    pom.packaging = "maven-plugin"
}
