package com.tactical.platform.speech

import android.content.Context
import com.tactical.domain.speech.ModelIdentifier
import com.tactical.platform.api.speech.ModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Implements ModelProvider by memory-mapping a model file out of app-internal storage — NOT directly out of APK assets, despite the
 * "assets/models/" phrasing in this class's architecture description.
 *
 * ASSUMPTION FLAGGED: a real random-access map of an APK asset (via
 * AssetManager.openFd()) only works when that asset is stored
 * *uncompressed* in the APK (requires an aaptOptions/androidResources
 * noCompress entry for the model file extensions). Rather than depend on
 * that build-config detail being set correctly, this class reads from the
 * plain file DynamicModelDownloadManager already extracted to
 * filesDir/models/ on first launch — a real file on disk always supports
 * FileChannel.map() regardless of how it was packaged. This means
 * getModelBuffer() is only expected to succeed AFTER
 * ModelDownloadManager.ensureModelAvailable(id) has reached Ready for
 * that id; callers skipping that step will get an IOException here.
 *
 * The two classes share the filesDir/models/ convention and the
 * "filename starts with id. Value" lookup (format-agnostic — works
 * whether the extracted file ends up as .tflite, .onnx, or anything
 * else) purely by matching constants/logic, not a shared interface.
 * Worth promoting to one shared internal object if a third class ever
 * needs the same convention.
 */
class AssetModelProvider(private val context: Context) : ModelProvider {

    override suspend fun getModelBuffer(id: ModelIdentifier): ByteBuffer = withContext(Dispatchers.IO) {
        val modelFile = modelsDir(context).listFiles { file -> file.name.startsWith("${id.value}.") }
            ?.firstOrNull()
            ?: throw IOException(
                "No extracted model file for '${id.value}' under ${modelsDir(context)} — " +
                        "call ModelDownloadManager.ensureModelAvailable(${id.value}) first"
            )

        FileInputStream(modelFile).channel.use { channel: FileChannel ->
            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()) as MappedByteBuffer
        }
    }

    companion object {
        // Shared with DynamicModelDownloadManager — both must resolve the
        // same directory. See ASSUMPTION FLAGGED note above re: promoting
        // this to a genuinely shared constant.
        fun modelsDir(context: Context) = context.filesDir.resolve("models")
    }
}