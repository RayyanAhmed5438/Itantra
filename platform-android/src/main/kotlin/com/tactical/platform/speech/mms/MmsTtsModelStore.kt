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
 * Stores the bundled MMS-TTS model pack under app-private storage.
 *
 * The ZIP is bundled into the APK at:
 *   assets/models/quantized_models.zip
 *
 * The archive is extracted lazily the first time the TTS Model Lab is opened.
 * The large ONNX files are kept outside the APK's asset-access path after
 * extraction so ONNX Runtime can memory-map/read ordinary files.
 */
@Singleton
class MmsTtsModelStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val rootDirectory: File
        get() = File(context.filesDir, "tts_models")

    suspend fun ensureBundledModelsAvailable(): Int = withContext(Dispatchers.IO) {
        val bundledInstalled = BUNDLED_LANGUAGES.count { isInstalled(it) }
        if (bundledInstalled == BUNDLED_LANGUAGES.size) {
            return@withContext bundledInstalled
        }

        val tempRoot = File(
            context.cacheDir,
            "tts_bundle_" + System.currentTimeMillis()
        )
        tempRoot.mkdirs()

        try {
            val input = context.assets.open(BUNDLED_ZIP_ASSET)

            ZipInputStream(
                BufferedInputStream(input, BUFFER_SIZE)
            ).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val normalized = entry.name.replace('\\', '/')
                    val relative = normalized.removePrefix(ZIP_ROOT + "/")

                    if (!normalized.startsWith(ZIP_ROOT + "/") || entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    val parts = relative.split('/')
                    if (parts.size != 2) {
                        zip.closeEntry()
                        continue
                    }

                    val language = MmsTtsLanguage.fromModelCode(parts[0])
                    if (
                        language == null ||
                        language !in BUNDLED_LANGUAGES ||
                        parts[1] !in ALLOWED_FILES
                    ) {
                        zip.closeEntry()
                        continue
                    }

                    val output = File(tempRoot, relative)
                    val canonicalRoot = tempRoot.canonicalFile
                    val canonicalOutput = output.canonicalFile
                    if (!canonicalOutput.path.startsWith(
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

            val completeLanguages = BUNDLED_LANGUAGES.filter {
                isComplete(tempRoot.resolve(it.modelCode))
            }

            if (completeLanguages.size != BUNDLED_LANGUAGES.size) {
                throw IOException(
                    "Bundled TTS archive is incomplete: " +
                        completeLanguages.size + "/" +
                        BUNDLED_LANGUAGES.size + " bundled language models found"
                )
            }

            rootDirectory.mkdirs()
            completeLanguages.forEach { language ->
                val source = tempRoot.resolve(language.modelCode)
                val destination = rootDirectory.resolve(language.modelCode)
                destination.deleteRecursively()
                if (!source.renameTo(destination)) {
                    source.copyRecursively(destination, overwrite = true)
                    source.deleteRecursively()
                }
            }

            completeLanguages.size
        } catch (e: IOException) {
            throw IOException(
                "Could not extract bundled TTS model pack from " +
                    BUNDLED_ZIP_ASSET + ": " + e.message,
                e
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    fun installedLanguages(): List<MmsTtsLanguage> =
        MmsTtsLanguage.ALL.filter { isInstalled(it) }

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

    private fun isComplete(directory: File): Boolean =
        REQUIRED_FILES.all {
            File(directory, it).isFile && File(directory, it).length() > 0L
        }

    companion object {
        private const val BUNDLED_ZIP_ASSET = "models/quantized_models.zip"
        private const val ZIP_ROOT = "quantized_models"
        private const val BUFFER_SIZE = 64 * 1024

        // Keep ALL languages available for future language support, but only
        // these models are shipped in the current bundled ZIP.
        private val BUNDLED_LANGUAGES = MmsTtsLanguage.ALL.filter {
            it.modelCode in BUNDLED_MODEL_CODES
        }

        private val BUNDLED_MODEL_CODES = setOf(
            "eng",
            "hin"
        )

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
