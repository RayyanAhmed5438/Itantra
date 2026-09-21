package com.tactical.platform.speech

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
 * Extracts the bundled Vosk Hindi small model into app-private storage.
 *
 * Asset:
 *   assets/models/vosk-model-small-hi-0.22.zip
 */
@Singleton
class VoskHindiSttModelStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rootDirectory: File
        get() = File(context.filesDir, "vosk/stt-small-hi")

    suspend fun ensureBundledModelAvailable(): File = withContext(Dispatchers.IO) {
        if (isComplete(rootDirectory)) return@withContext rootDirectory

        val tempRoot = File(
            context.cacheDir,
            "vosk_hi_bundle_" + System.currentTimeMillis()
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

                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }

                        val prefix = MODEL_ROOT + "/"
                        if (!normalized.startsWith(prefix)) {
                            zip.closeEntry()
                            continue
                        }

                        val relative = normalized.removePrefix(prefix)
                        if (relative.isBlank()) {
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
            }

            if (!isComplete(tempRoot)) {
                throw IOException("Bundled Vosk Hindi model is incomplete")
            }

            rootDirectory.deleteRecursively()
            rootDirectory.parentFile?.mkdirs()

            if (!tempRoot.renameTo(rootDirectory)) {
                tempRoot.copyRecursively(rootDirectory, overwrite = true)
                tempRoot.deleteRecursively()
            }

            rootDirectory
        } catch (e: IOException) {
            throw IOException(
                "Could not extract bundled Vosk Hindi model from " +
                    BUNDLED_ZIP_ASSET + ": " + (e.message ?: "unknown error"),
                e
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun isComplete(directory: File): Boolean =
        REQUIRED_FILES.all { relative ->
            val file = File(directory, relative)
            file.isFile && file.length() > 0L
        }

    companion object {
        private const val BUNDLED_ZIP_ASSET =
            "models/vosk-model-small-hi-0.22.zip"
        private const val MODEL_ROOT = "vosk-model-small-hi-0.22"
        private const val BUFFER_SIZE = 64 * 1024

        private val REQUIRED_FILES = setOf(
            "graph/phones/word_boundary.int",
            "graph/Gr.fst",
            "graph/HCLr.fst",
            "graph/disambig_tid.int",
            "am/final.mdl",
            "README",
            "conf/model.conf",
            "conf/mfcc.conf",
            "ivector/final.dubm",
            "ivector/global_cmvn.stats",
            "ivector/final.ie",
            "ivector/final.mat",
            "ivector/splice.conf",
            "ivector/online_cmvn.conf"
        )
    }
}
