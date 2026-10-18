plugins { alias(libs.plugins.kotlin.jvm); java }

tasks.matching { it.name == "compileJava" || it.name == "compileKotlin" }.configureEach {
    dependsOn(":distribution:copyDeveloperArtifacts")
}

val distribution = files(project(":distribution").layout.buildDirectory.file(
    "libs/pnLibrary-${project.version}-bukkit-java8.jar",
)).builtBy(":distribution:shadowBukkit")

dependencies {
    compileOnly(distribution)
    compileOnly(libs.spigot.api.v18)
}
