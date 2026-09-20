plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "com.tactical.platform"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 28
        // targetSdk is not set on library modules (AGP ignores it there);
        // the app module's targetSdk governs runtime behavior.
    }

    buildFeatures {
        // No Compose/View binding needed here — this module is pure
        // hardware/model glue, no UI.
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    packaging {
        // TFLite/ONNX/Moonshine ship native .so libs for multiple ABIs.
        // Avoid duplicate-file merge failures from transitive dependencies.
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-platform-api"))
    implementation(project(":core-protocol"))
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.tensorflow.lite)
    implementation(libs.onnxruntime.android)
    implementation(libs.moonshine.voice)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
