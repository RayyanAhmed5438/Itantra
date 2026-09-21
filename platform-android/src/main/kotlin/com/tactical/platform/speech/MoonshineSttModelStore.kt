package com.tactical.platform.speech

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the bundled English Moonshine Tiny Streaming model into app-private
 * storage. The ZIP itself lives in the app module's assets so it can remain
 * uncommitted while still being packaged into local debug/release builds.
 *
 * No network access is used here.
 */
@Singleton
class MoonshineSttModelStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rootDirectory: File
        get() = File(context.filesDir, "moonshine/stt-tiny-en")

    suspend fun ensureBundledModelAvailable(): File = withContext(Dispatchers.IO) {
        if (isComplete(rootDirectory)) {
            return@withContext rootDirectory
        }

        val tempRoot = File(
            context.cacheDir,
            "moonshine_stt_" + System.currentTimeMillis()
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

                        if (
                            !normalized.startsWith("$ZIP_ROOT/") ||
                            entry.isDirectory
                        ) {
                            zip.closeEntry()
                            continue
                        }

                        val relative = normalized.removePrefix("$ZIP_ROOT/")
                        if (relative !in REQUIRED_FILES) {
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

            if (!isComplete(tempRoot)) {
                throw IOException("Bundled Moonshine Tiny Streaming model is incomplete")
            }

            rootDirectory.deleteRecursively()
            rootDirectory.parentFile?.mkdirs()

            if (!tempRoot.renameTo(rootDirectory)) {
                tempRoot.copyRecursively(rootDirectory, overwrite = true)
                tempRoot.deleteRecursively()
            }

            rootDirectory
        } catch (e: IOException) {
            tempRoot.deleteRecursively()
            throw IOException(
                "Could not extract bundled Moonshine STT model: " +
                    e.message,
                e
            )
        }
    }

    private fun isComplete(directory: File): Boolean =
        REQUIRED_FILES.all {
            val file = File(directory, it)
            file.isFile && file.length() > 0L
        }

    companion object {
        private const val BUNDLED_ZIP_ASSET =
            "models/moonshine_stt_tiny_en.zip"
        private const val ZIP_ROOT = "moonshine_stt_tiny_en"
        private const val BUFFER_SIZE = 64 * 1024

        private val REQUIRED_FILES = setOf(
            "adapter.ort",
            "cross_kv.ort",
            "decoder_kv.ort",
            "encoder.ort",
            "frontend.model.ort",
            "frontend.weights.ort",
            "streaming_config.json",
            "tokenizer.bin"
        )
    }
}
