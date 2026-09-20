package com.tactical.platform.speech.mms

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MmsTtsModelStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val rootDirectory: File
        get() = File(context.filesDir, "tts_models")

    suspend fun importZip(uri: Uri): Int = withContext(Dispatchers.IO) {
        val tempRoot = File(context.cacheDir, "tts_import_" + System.currentTimeMillis())
        tempRoot.mkdirs()

        try {
            var importedLanguages = emptySet<String>()

            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open selected ZIP file")

            ZipInputStream(BufferedInputStream(input, BUFFER_SIZE)).use { zip ->
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
                    if (language == null || parts[1] !in ALLOWED_FILES) {
                        zip.closeEntry()
                        continue
                    }

                    val output = File(tempRoot, relative)
                    val canonicalRoot = tempRoot.canonicalFile
                    val canonicalOutput = output.canonicalFile
                    if (!canonicalOutput.path.startsWith(canonicalRoot.path + File.separator)) {
                        throw IOException("Unsafe ZIP entry: " + entry.name)
                    }

                    output.parentFile?.mkdirs()
                    output.outputStream().use { out ->
                        zip.copyTo(out, BUFFER_SIZE)
                    }
                    importedLanguages = importedLanguages + language.modelCode
                    zip.closeEntry()
                }
            }

            val validLanguages = importedLanguages
                .mapNotNull(MmsTtsLanguage::fromModelCode)
                .filter { isComplete(tempRoot.resolve(it.modelCode)) }

            if (validLanguages.isEmpty()) {
                throw IOException(
                    "The ZIP did not contain a complete MMS-TTS language model. " +
                        "Expected model.int8.onnx, vocab.json, tokenizer_config.json and config.json."
                )
            }

            rootDirectory.mkdirs()
            validLanguages.forEach { language ->
                val source = tempRoot.resolve(language.modelCode)
                val destination = rootDirectory.resolve(language.modelCode)
                destination.deleteRecursively()
                if (!source.renameTo(destination)) {
                    source.copyRecursively(destination, overwrite = true)
                    source.deleteRecursively()
                }
            }

            validLanguages.size
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
        REQUIRED_FILES.all { File(directory, it).isFile && File(directory, it).length() > 0L }

    companion object {
        private const val ZIP_ROOT = "quantized_models"
        private const val BUFFER_SIZE = 64 * 1024
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
