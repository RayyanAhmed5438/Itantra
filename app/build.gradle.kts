import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.devtools.ksp)
}

val moonshineModelDir = layout.projectDirectory.dir("src/main/assets/models").asFile
val moonshineModelZip = File(moonshineModelDir, "moonshine_stt_tiny_en.zip")
val moonshineModelBaseUrl =
    "https://download.moonshine.ai/model/tiny-streaming-en/quantized_26_08_21"

val moonshineModelFiles = listOf(
    "adapter.ort",
    "cross_kv.ort",
    "decoder_kv.ort",
    "encoder.ort",
    "frontend.model.ort",
    "frontend.weights.ort",
    "streaming_config.json",
    "tokenizer.bin"
)

val prepareMoonshineModel = tasks.register("prepareMoonshineModel") {
    outputs.file(moonshineModelZip)

    doLast {
        if (moonshineModelZip.isFile && moonshineModelZip.length() > 0L) {
            logger.lifecycle("Moonshine Tiny Streaming model already present: ${moonshineModelZip.name}")
            return@doLast
        }

        moonshineModelDir.mkdirs()
        val tempZip = File(moonshineModelDir, ".moonshine_stt_tiny_en.zip.tmp")

        try {
            logger.lifecycle("Downloading Moonshine Tiny Streaming English model...")
            ZipOutputStream(tempZip.outputStream().buffered()).use { zip ->
                moonshineModelFiles.forEach { fileName ->
                    val url = URL("$moonshineModelBaseUrl/$fileName")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 20_000
                        readTimeout = 120_000
                        setRequestProperty("User-Agent", "Itantra-Gradle-Moonshine/1.0")
                        instanceFollowRedirects = true
                    }

                    try {
                        val status = connection.responseCode
                        check(status in 200..299) {
                            "HTTP $status while downloading $fileName"
                        }

                        zip.putNextEntry(ZipEntry("moonshine_stt_tiny_en/$fileName"))
                        connection.inputStream.buffered().use { input ->
                            input.copyTo(zip, 64 * 1024)
                        }
                        zip.closeEntry()
                        logger.lifecycle("  downloaded $fileName")
                    } finally {
                        connection.disconnect()
                    }
                }
            }

            check(tempZip.isFile && tempZip.length() > 0L) {
                "Moonshine model archive was not created"
            }

            if (moonshineModelZip.exists()) {
                check(moonshineModelZip.delete()) {
                    "Could not replace existing ${moonshineModelZip.absolutePath}"
                }
            }

            check(tempZip.renameTo(moonshineModelZip)) {
                "Could not move temporary Moonshine model archive into place"
            }

            logger.lifecycle(
                "Moonshine Tiny Streaming model ready: ${moonshineModelZip.length()} bytes"
            )
        } catch (e: Exception) {
            tempZip.delete()
            throw GradleException(
                "Could not prepare bundled Moonshine Tiny Streaming English model. " +
                    "Check internet access and try the build again.",
                e
            )
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareMoonshineModel)
}

android {
    namespace = "com.tactical.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.tactical.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INDEX/*"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-protocol"))
    implementation(project(":core-platform-api"))
    implementation(project(":platform-android"))
    implementation(project(":engine-discovery"))
    implementation(project(":engine-mesh"))
    implementation(project(":engine-speech"))
    implementation(project(":feature-ptt"))
    implementation(project(":feature-emergency"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
}
