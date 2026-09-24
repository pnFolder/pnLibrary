plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnLibrary-compatibility-bukkit" }

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach { options.release = 8 }
tasks.withType<Test>().configureEach { useJUnitPlatform() }

dependencies {
    api(project(":modules:design:console"))
    compileOnly(libs.spigot.api.v18)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-compatibility-bukkit", project.version.toString())
    pom {
        name.set("pnLibrary Bukkit Compatibility Guard")
        description.set("Standalone version guard for Bukkit plugins using pnLibrary.")
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
        scm { url.set("https://github.com/pnFolder/pnLibrary") }
    }
}
