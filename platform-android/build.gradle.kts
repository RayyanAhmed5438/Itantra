import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity



plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.devtools.ksp)
}
@CacheableTransform
abstract class StripMoonshineOrtTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val output = outputs.file(input.nameWithoutExtension + "-patched.aar")

        ZipInputStream(
            BufferedInputStream(input.inputStream())
        ).use { source ->
            ZipOutputStream(
                BufferedOutputStream(output.outputStream())
            ).use { target ->
                while (true) {
                    val entry = source.nextEntry ?: break
                    val normalized = entry.name.replace('\\', '/')
                    val isMoonshineBundledOrt =
                        normalized.startsWith("jni/") &&
                            normalized.substringAfterLast('/') == "libonnxruntime.so"

                    if (!isMoonshineBundledOrt) {
                        val copied = ZipEntry(normalized)
                        if (entry.time >= 0L) {
                            copied.time = entry.time
                        }
                        target.putNextEntry(copied)
                        if (!entry.isDirectory) {
                            source.copyTo(target, 64 * 1024)
                        }
                        target.closeEntry()
                    }

                    source.closeEntry()
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

val moonshinePatchedAttribute =
    Attribute.of("com.tactical.moonshine.patched", Boolean::class.javaObjectType)

dependencies {
    components {
        withModule("ai.moonshine:moonshine-voice") {
            allVariants {
                attributes {
                    attribute(moonshinePatchedAttribute, false)
                }
            }
        }
    }

    attributesSchema {
        attribute(moonshinePatchedAttribute)
    }

    artifactTypes {
        maybeCreate("aar").attributes.attribute(moonshinePatchedAttribute, false)
    }

    // Patch Moonshine's AAR inside Gradle's dependency graph so its bundled
    // minimal ORT does not collide with the full Microsoft ORT used by MMS TTS.
    registerTransform(StripMoonshineOrtTransform::class.java) {
        from.attribute(
            org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
            "aar"
        )
        from.attribute(moonshinePatchedAttribute, false)
        to.attribute(
            org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
            "aar"
        )
        to.attribute(moonshinePatchedAttribute, true)
    }

    implementation("ai.moonshine:moonshine-voice:" + libs.versions.moonshineVoice.get()) {
        attributes {
            attribute(moonshinePatchedAttribute, true)
        }
    }

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