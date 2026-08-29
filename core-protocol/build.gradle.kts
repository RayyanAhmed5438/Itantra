plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core-domain"))

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}
