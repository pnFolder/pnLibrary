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
    compileOnly(project(":modules:api"))
}
