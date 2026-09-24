plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnLibrary-remote-check-bukkit" }
java { withSourcesJar(); withJavadocJar(); sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
tasks.withType<JavaCompile>().configureEach { options.release = 8 }
tasks.withType<Test>().configureEach { useJUnitPlatform() }
dependencies {
    api(project(":modules:common"))
    api(project(":modules:design:console"))
    compileOnly(libs.spigot.api.v18)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
mavenPublishing {
    publishToMavenCentral(); signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-remote-check-bukkit", project.version.toString())
    pom { name.set("pnLibrary Remote Check for Bukkit"); description.set("Verified remote policy checks loaded by Bukkit plugins."); url.set("https://github.com/pnFolder/pnLibrary") }
}
