// pnlibrary-core — no server-platform classes, JVM 8 bytecode

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}
base { archivesName = "pnLibrary-core" }

// The packaged-JAR process test has its own distribution verification task.
tasks.named<Test>("test") { exclude("**/OfflineUpdaterJarTest.class", "**/NativeProxyUpdateTest.class") }

tasks.register<Test>("verifyNativeProxyUpdates") {
    group = "verification"
    description = "Runs isolated real Velocity and Bungee update/restart/configuration rollback tests"
    dependsOn("testClasses", ":distribution:shadowVelocity", ":distribution:shadowBungee", ":distribution:shadowUpdater")
    mustRunAfter(":distribution:copyDeveloperArtifacts")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { excludeTags("paper") }
    include("**/NativeProxyUpdateTest.class")
    outputs.upToDateWhen { false }
    systemProperty("pnlibrary.native.fixtures", rootProject.layout.buildDirectory.dir("update-catalog-native").get().asFile.absolutePath)
    systemProperty("pnlibrary.native.version", rootProject.version.toString())
    systemProperty("pnlibrary.native.distribution", project(":distribution").layout.buildDirectory.dir("libs").get().asFile.absolutePath)
    listOf("velocityServerJar", "bungeeServerJar").forEach { name ->
        val server = providers.gradleProperty(name)
        if (server.isPresent) { inputs.file(server); systemProperty("pnlibrary.native.$name", server.get()) }
        doFirst { require(server.isPresent) { "Supply -P$name=<existing server JAR>; no server download or existing server mutation is performed" } }
    }
}

tasks.register<Test>("verifyNativeBukkitUpdates") {
    group = "verification"
    description = "Runs an isolated Paper update/restart/configuration rollback test with an explicitly supplied accepted EULA"
    dependsOn("testClasses", ":distribution:shadowBukkit", ":distribution:shadowUpdater")
    mustRunAfter(":distribution:copyDeveloperArtifacts")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("paper") }
    include("**/NativeProxyUpdateTest.class")
    outputs.upToDateWhen { false }
    systemProperty("pnlibrary.native.fixtures", rootProject.layout.buildDirectory.dir("update-catalog-native").get().asFile.absolutePath)
    systemProperty("pnlibrary.native.version", rootProject.version.toString())
    systemProperty("pnlibrary.native.distribution", project(":distribution").layout.buildDirectory.dir("libs").get().asFile.absolutePath)
    listOf("paperServerJar", "paperEulaFile").forEach { name ->
        val source = providers.gradleProperty(name)
        if (source.isPresent) { inputs.file(source); systemProperty("pnlibrary.native.$name", source.get()) }
        doFirst { require(source.isPresent) { "Supply -P$name=<existing file>; the test cannot accept the Minecraft EULA for you" } }
    }
}

tasks.register<Test>("verifyUpdaterProcess") {
    group = "verification"
    description = "Tests the executable updater in separate JVM processes"
    dependsOn(":distribution:shadowUpdater", "testClasses")
    mustRunAfter(":distribution:copyDeveloperArtifacts")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    include("**/OfflineUpdaterJarTest.class")
    val updaterJar = project(":distribution").layout.buildDirectory.file("libs/pnLibrary-updater-${rootProject.version}.jar")
    inputs.file(updaterJar)
    systemProperty("pnlibrary.updater.jar", updaterJar.get().asFile.absolutePath)
}

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:runtime-spi"))
    implementation(project(":modules:features:currency-runtime"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.gson)
    implementation(libs.snakeyaml)
    implementation(libs.adventure.minimessage)
    implementation(libs.adventure.legacy)
    implementation(libs.adventure.gson)
    implementation(libs.adventure.plain)
    compileOnly(libs.faststats.core.j8)
    implementation(project(":modules:design:console"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.launcher)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.pnfolder",
        artifactId = "pnlibrary-core",
        version = project.version.toString()
    )

    pom {
        name.set("pnLibrary Core")
        description.set("Core implementation shared by pnLibrary platform modules.")
        inceptionYear.set("2026")
        url.set("https://github.com/pnFolder/pnLibrary")

        licenses {
            license {
                name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                url.set(providers.gradleProperty("POM_LICENSE_URL"))
                distribution.set(providers.gradleProperty("POM_LICENSE_DIST").orElse("repo"))
            }
        }

        developers {
            developer {
                id.set("pnFolder")
                name.set("pnFolder")
                url.set("https://github.com/pnFolder")
            }
        }

        scm {
            url.set("https://github.com/pnFolder/pnLibrary")
            connection.set("scm:git:git://github.com/pnFolder/pnLibrary.git")
            developerConnection.set("scm:git:ssh://git@github.com/pnFolder/pnLibrary.git")
        }
    }
}
