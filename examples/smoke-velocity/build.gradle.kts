plugins { alias(libs.plugins.kotlin.jvm); java }

tasks.matching { it.name == "compileJava" || it.name == "compileKotlin" }.configureEach {
    dependsOn(":distribution:copyDeveloperArtifacts")
}

val distribution = files(project(":distribution").layout.buildDirectory.file(
    "libs/pnLibrary-${project.version}-velocity-java17.jar",
)).builtBy(":distribution:shadowVelocity")

dependencies {
    compileOnly(distribution)
    compileOnly(libs.velocity.api)
}
