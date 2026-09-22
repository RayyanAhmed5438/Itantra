package com.tactical.platform.speech.mms

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores bundled MMS-TTS models under app-private storage.
 *
 * The complete ZIP remains in the APK. Individual language models are
 * extracted into filesDir/tts_models only when that language is first needed.
 * This keeps disk usage and extraction work proportional to the languages
 * actually used, while the TTS engine itself keeps only one ONNX session
 * resident in memory at a time.
 */
@Singleton
class MmsTtsModelStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val rootDirectory: File
        get() = File(context.filesDir, "tts_models")

    suspend fun ensureBundledModelAvailable(
        language: MmsTtsLanguage
    ): File = withContext(Dispatchers.IO) {
        require(language in BUNDLED_LANGUAGES) {
            "TTS model for " + language.displayName + " is not bundled in this APK"
        }

        val destination = rootDirectory.resolve(language.modelCode)
        if (isComplete(destination)) {
            return@withContext destination
        }

        val tempRoot = File(
            context.cacheDir,
            "tts_model_" + language.modelCode + "_" + System.currentTimeMillis()
        )
        tempRoot.mkdirs()

        try {
            context.assets.open(BUNDLED_ZIP_ASSET).use { input ->
                ZipInputStream(
                    BufferedInputStream(input, BUFFER_SIZE)
                ).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val normalized = entry.name.replace('\\', '/')
                        val relative = normalized.removePrefix(ZIP_ROOT + "/")

                        if (
                            !normalized.startsWith(ZIP_ROOT + "/") ||
                            entry.isDirectory
                        ) {
                            zip.closeEntry()
                            continue
                        }

                        val parts = relative.split('/')
                        if (
                            parts.size != 2 ||
                            parts[0] != language.modelCode ||
                            parts[1] !in ALLOWED_FILES
                        ) {
                            zip.closeEntry()
                            continue
                        }

                        val output = File(tempRoot, relative)
                        val canonicalRoot = tempRoot.canonicalFile
                        val canonicalOutput = output.canonicalFile

                        if (
                            !canonicalOutput.path.startsWith(
                                canonicalRoot.path + File.separator
                            )
                        ) {
                            throw IOException("Unsafe ZIP entry: " + entry.name)
                        }

                        output.parentFile?.mkdirs()
                        output.outputStream().use { out ->
                            zip.copyTo(out, BUFFER_SIZE)
                        }
                        zip.closeEntry()
                    }
                }
            }

            if (!isComplete(tempRoot.resolve(language.modelCode))) {
                throw IOException(
                    "Bundled TTS model is incomplete for " +
                        language.displayName
                )
            }

            rootDirectory.mkdirs()
            destination.deleteRecursively()

            val source = tempRoot.resolve(language.modelCode)
            if (!source.renameTo(destination)) {
                source.copyRecursively(destination, overwrite = true)
                source.deleteRecursively()
            }

            destination
        } catch (e: IOException) {
            throw IOException(
                "Could not extract bundled TTS model for " +
                    language.displayName + ": " + (e.message ?: "unknown error"),
                e
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    /**
     * Compatibility helper for the TTS Model Lab or diagnostics.
     * Normal message playback does NOT call this; it extracts only the
     * language actually requested.
     */
    suspend fun ensureBundledModelsAvailable(): Int = withContext(Dispatchers.IO) {
        BUNDLED_LANGUAGES.count { language ->
            runCatching {
                ensureBundledModelAvailable(language)
                true
            }.getOrDefault(false)
        }
    }

    fun bundledLanguages(): List<MmsTtsLanguage> = BUNDLED_LANGUAGES

    fun installedLanguages(): List<MmsTtsLanguage> =
        BUNDLED_LANGUAGES.filter { isInstalled(it) }

    fun isInstalled(language: MmsTtsLanguage): Boolean =
        isComplete(rootDirectory.resolve(language.modelCode))

    fun modelDirectory(language: MmsTtsLanguage): File {
        val directory = rootDirectory.resolve(language.modelCode)
        require(isComplete(directory)) {
            "TTS model for " + language.displayName + " is not installed under " +
                directory.absolutePath
        }
        return directory
    }

    private fun isComplete(directory: File): Boolean {
        if (!directory.isDirectory) return false
        return REQUIRED_FILES.all { name ->
            val file = File(directory, name)
            file.isFile && file.length() > 0L
        }
    }

    companion object {
        private const val BUNDLED_ZIP_ASSET = "models/quantized_models.zip"
        private const val ZIP_ROOT = "quantized_models"
        private const val BUFFER_SIZE = 64 * 1024

        private val BUNDLED_MODEL_CODES = setOf(
            "eng",
            "hin"
        )

        // Current APK bundle contains English and Hindi MMS-TTS models.
        private val BUNDLED_LANGUAGES = MmsTtsLanguage.ALL.filter {
            it.modelCode in BUNDLED_MODEL_CODES
        }

        private val ALLOWED_FILES = setOf(
            "config.json",
            "tokenizer_config.json",
            "vocab.json",
            "tokens.txt",
            "model.int8.onnx"
        )

        private val REQUIRED_FILES = setOf(
            "config.json",
            "tokenizer_config.json",
            "vocab.json",
            "model.int8.onnx"
        )
    }
}
