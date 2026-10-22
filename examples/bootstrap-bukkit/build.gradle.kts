import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    alias(libs.plugins.shadow)
}

base { archivesName = "${rootProject.property("pnlibrary.name")}-bootstrap-demo" }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    implementation(project(":platforms:bukkit:bootstrap"))
    // Provided at runtime by pnLibrary itself, which this plugin installs when it is missing.
    compileOnly(project(":modules:api"))
    compileOnly(project(":platforms:bukkit:api"))
    compileOnly(libs.spigot.api.v18)
}

val resourceVersion = rootProject.version.toString()
tasks.named<ProcessResources>("processResources") {
    inputs.property("version", resourceVersion)
    filesMatching("plugin.yml") { expand(mapOf("version" to resourceVersion)) }
}

tasks.named<Jar>("jar") { enabled = false }
tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = "bukkit-java8"
    // The installer ships inside the plugin; keep it clear of other plugins that embed it.
    relocate("org.pnfolder.pnlibrary.bootstrap", "org.pnfolder.demo.libs.bootstrap")
    relocate("org.pnfolder.pnlibrary.bukkit.bootstrap", "org.pnfolder.demo.libs.bukkit")
    relocate("org.pnfolder.pnlibrary.console", "org.pnfolder.demo.libs.console")
    relocate("com.google.gson", "org.pnfolder.demo.libs.gson")
}
tasks.named("assemble") { dependsOn("shadowJar") }
