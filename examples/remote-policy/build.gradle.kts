plugins {
    `java-library`
}

base { archivesName = "pncases-remote-policy" }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    // The policy is compiled only against the stable remote-check contract.
    compileOnly(project(":modules:remote-check:bukkit"))
    compileOnly(libs.spigot.api.v18)
}
