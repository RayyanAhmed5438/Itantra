plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-protocol"))
    implementation(project(":core-platform-api"))


    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    implementation(libs.kotlinx.coroutines.test)}

kotlin {
    jvmToolchain(17)
}
