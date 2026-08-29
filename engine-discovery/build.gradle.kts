plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-protocol"))
    implementation(project(":core-platform-api"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

kotlin {
    jvmToolchain(17)
}
