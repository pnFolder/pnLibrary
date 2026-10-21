plugins {
    `java-library`
}

base { archivesName = "pnLibrary-remote-policy-example" }

dependencies {
    compileOnly(project(":modules:api"))
}
