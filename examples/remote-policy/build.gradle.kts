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
    // The policy is compiled against the contract shipped by the Bukkit runtime.
    compileOnly(project(":platforms:bukkit:runtime"))
    compileOnly(libs.spigot.api.v18)
}
