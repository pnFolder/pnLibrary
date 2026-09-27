plugins {
    java
    id("ru.privatenull.pnlibrary.component-metadata")
}

base { archivesName = "pnLibrary-acceptance" }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}

tasks.named<Jar>("jar") {
    archiveClassifier = "bukkit-java8"
}

dependencies {
    compileOnly(project(":modules:api"))
    compileOnly(project(":modules:features:update"))
    compileOnly(project(":platforms:bukkit:api"))
    compileOnly(libs.spigot.api.v18)
}

val resourceVersion = project.version.toString()
tasks.named<ProcessResources>("processResources") {
    inputs.property("version", resourceVersion)
    filesMatching("plugin.yml") { expand(mapOf("version" to resourceVersion)) }
}

pnComponentMetadata {
    id.set("acceptance")
    version.set(resourceVersion)
    apiVersions(1, 1)
    channel.set("beta")
    javaMinimum.set(8)
}



tasks.register<Sync>("assembleAcceptanceKit") {
    group = "distribution"
    description = "Builds and collects the Bukkit runtime and acceptance plugin JARs"
    dependsOn(":distribution:shadowBukkit", ":distribution:copyDeveloperArtifacts", "jar")
    from(project(":distribution").layout.buildDirectory.dir("libs")) {
        include("pnLibrary-*-bukkit-java8.jar")
    }
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("acceptance-bukkit"))
}
