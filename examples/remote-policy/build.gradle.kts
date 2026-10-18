plugins {
    `java-library`
}

base { archivesName = "pncases-remote-policy" }

dependencies {
    compileOnly(project(":modules:api"))
}
