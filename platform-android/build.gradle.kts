import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.devtools.ksp)
}

abstract class PatchMoonshineAarTask : DefaultTask() {
    @get:InputFiles
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun patch() {
        val source = sourceFiles.singleFile
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()

        ZipInputStream(
            BufferedInputStream(source.inputStream())
        ).use { input ->
            ZipOutputStream(
                BufferedOutputStream(target.outputStream())
            ).use { out ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val normalized = entry.name.replace('\\', '/')
                    val isMoonshineBundledOrt =
                        normalized.startsWith("jni/") &&
                            normalized.substringAfterLast('/') == "libonnxruntime.so"

                    if (!isMoonshineBundledOrt) {
                        val copied = ZipEntry(normalized)
                        if (entry.time >= 0L) {
                            copied.time = entry.time
                        }
                        out.putNextEntry(copied)
                        if (!entry.isDirectory) {
                            input.copyTo(out, 64 * 1024)
                        }
                        out.closeEntry()
                    }

                    input.closeEntry()
                }
            }
        }
    }
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
        // TFLite/ONNX ship native .so libs for multiple ABIs; avoid
        // duplicate-file merge failures from transitive deps.
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

val moonshineSource = configurations.create("moonshineSource") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val patchedMoonshineAar = file(
    "libs/moonshine-voice-${libs.versions.moonshineVoice.get()}-no-ort.aar"
)

val patchMoonshineAar = tasks.register<PatchMoonshineAarTask>("patchMoonshineAar") {
    sourceFiles.from(moonshineSource)
    outputFile.set(patchedMoonshineAar)
}

dependencies {
    // Moonshine Voice is bundled through a build-time patched AAR so its
    // private minimal libonnxruntime.so does not collide with the full
    // Microsoft ORT used by the existing MMS TTS engine.
    add("moonshineSource", "ai.moonshine:moonshine-voice:${libs.versions.moonshineVoice.get()}")
    implementation(files(patchedMoonshineAar).builtBy(patchMoonshineAar))

    // Contracts this module implements
    implementation(project(":core-domain"))
    implementation(project(":core-platform-api"))
    implementation(project(":core-protocol"))

    // Coroutines (callbackFlow for BLE/Wi-Fi callback-based APIs)
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Moonshine's AAR compiles against these runtime dependencies.
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime:2.9.1")

    // On-device inference backends — `implementation`, never `api`, so
    // dependent modules (engine-speech, feature-ptt, app) never see these
    // types directly. SpeechBackendModule is the only seam.
    implementation(libs.tensorflow.lite)
    implementation(libs.onnxruntime.android)

//    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
//    testImplementation(libs.robolectric)
}