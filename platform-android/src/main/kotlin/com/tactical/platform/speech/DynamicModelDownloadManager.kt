package com.tactical.platform.speech

import android.content.Context
import com.tactical.domain.speech.ModelIdentifier
import com.tactical.domain.speech.ModelStatus
import com.tactical.platform.api.speech.ModelDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException

/**
 * Implements ModelDownloadManager. "Download" here is purely local: APK
 * asset (assets/models/) -> app-internal storage (filesDir/models/, see
 * AssetModelProvider.modelsDir), done once on first launch. Nothing ever
 * touches the network - models ship inside the APK itself.
 * Extraction is necessary b/c Assets are compressed by default - see that
 * class's KDoc for the full reasoning
 */

class DynamicModelDownloadManager(private val context: Context) : ModelDownloadManager {

    override fun ensureModelAvailable(id: ModelIdentifier): Flow<ModelStatus> = flow {
        val destinationDir = AssetModelProvider.modelsDir(context).apply { mkdirs() }

        val alreadyExtracted = destinationDir.listFiles { file ->
            file.name.startsWith("${id.value}.") && !file.name.endsWith(PART_SUFFIX)
        }?.firstOrNull()
        if (alreadyExtracted != null) {
            emit(ModelStatus.Ready)
            return@flow
        }

        val assetFileName = context.assets.list(ASSETS_MODELS_DIR)?.firstOrNull { it.startsWith("${id.value}.") }
        if (assetFileName == null) {
            // Genuinely not bundled in this APK build — distinct from a
            // copy that started and failed (Failed, below).
            emit(ModelStatus.Missing)
            return@flow
        }

        emit(ModelStatus.Loading(0f))

        val destinationFile = File(destinationDir, assetFileName)
        val tempFile = File(destinationDir, "$assetFileName$PART_SUFFIX")
        tempFile.delete() // clear any stray leftover from a previous crashed attempt

        try {
            // ASSUMPTION FLAGGED: architecture.md's kdoc says this class
            // "Emits Failed(reason) if extraction/checksum fails," but no
            // expected-checksum source (a sidecar hash file, a hardcoded
            // map, anything) exists anywhere in core-domain or this tree.
            // What's verified below is copy integrity only — bytes
            // written match bytes read from the asset stream, catching a
            // truncated write (e.g. disk full mid-copy) — NOT a
            // cryptographic checksum of the asset's correctness or
            // authenticity. Real checksum verification needs that
            // expected-hash source designed and added first.
            val totalBytes = runCatching { context.assets.openFd("$ASSETS_MODELS_DIR/$assetFileName").length }.getOrDefault(-1L)
            var bytesCopied = 0L

            context.assets.open("$ASSETS_MODELS_DIR/$assetFileName").use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes > 0) {
                            emit(ModelStatus.Loading((bytesCopied.toFloat() / totalBytes).coerceIn(0f, 1f)))
                        }
                    }
                }
            }

            if (totalBytes > 0 && bytesCopied != totalBytes) {
                tempFile.delete()
                emit(ModelStatus.Failed("copy incomplete for '${id.value}': expected $totalBytes bytes, wrote $bytesCopied"))
                return@flow
            }

            if (!tempFile.renameTo(destinationFile)) {
                tempFile.delete()
                emit(ModelStatus.Failed("could not finalize extracted file for '${id.value}'"))
                return@flow
            }

            emit(ModelStatus.Ready)
        } catch (e: IOException) {
            tempFile.delete()
            emit(ModelStatus.Failed("extraction failed for '${id.value}': ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val ASSETS_MODELS_DIR = "models"
        private const val COPY_BUFFER_SIZE = 64 * 1024
        private const val PART_SUFFIX = ".part"
    }
}