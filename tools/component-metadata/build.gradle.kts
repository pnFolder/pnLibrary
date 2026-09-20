plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
    plugins {
        create("pnComponent") {
            id = "ru.privatenull.pnlibrary.component"
            implementationClass = "ru.privatenull.pnlibrary.gradle.PnComponentPlugin"
        }
    }
}

dependencies {
    implementation(libs.gson)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
