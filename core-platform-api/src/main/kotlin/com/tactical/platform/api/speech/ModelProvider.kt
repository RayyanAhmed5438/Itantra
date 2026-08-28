package com.tactical.platform.api.speech

import com.tactical.domain.speech.ModelIdentifier
import java.nio.ByteBuffer

/**
 * Gives speech backends access to a model's raw bytes. Implemented in
 * platform-android as AssetModelProvider, loading any file under
 * assets/models/ as a MappedByteBuffer by ModelIdentifier — format
 * agnostic, so it works for both the .tflite/.onnx STT model and whatever
 * format the TTS model uses without this interface caring which.
 *
 * The returned buffer is read-only and remains valid for the lifetime of
 * the process — callers (TfliteSpeechToText, etc.) can hold onto it rather
 * than re-fetching per inference call.
 */
interface ModelProvider {

    /**
     * Returns a memory-mapped buffer for the given model asset.
     */
    suspend fun getModelBuffer(id: ModelIdentifier): ByteBuffer
}