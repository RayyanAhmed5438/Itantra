package com.tactical.app

import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tactical.platform.speech.MoonshineSttModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MoonshineBundledSttTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun bundledModelExtractsLocally() = runBlocking {
        // Force the first-use extraction path instead of reusing an old
        // model directory from a previous test run.
        File(context.filesDir, "moonshine/stt-tiny-en").deleteRecursively()

        context.assets.open(BUNDLED_ZIP_ASSET).use { input ->
            assertTrue(
                "Bundled Moonshine ZIP is missing from the APK assets",
                input.available() > 0
            )
        }

        val modelDirectory = MoonshineSttModelStore(context)
            .ensureBundledModelAvailable()

        REQUIRED_FILES.forEach { name ->
            val file = File(modelDirectory, name)
            assertTrue("Missing extracted model file: $name", file.isFile)
            assertTrue("Extracted model file is empty: $name", file.length() > 0L)
        }
    }

    @Test
    fun moonshineTinyCanLoadBundledModel() = runBlocking {
        File(context.filesDir, "moonshine/stt-tiny-en").deleteRecursively()

        val modelDirectory = MoonshineSttModelStore(context)
            .ensureBundledModelAvailable()

        val transcriber = Transcriber()
        transcriber.setUpdateInterval(0.5)
        transcriber.loadFromFiles(
            modelDirectory.absolutePath,
            JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING
        )

        // Reaching this point proves the packaged native library and all
        // required Tiny Streaming model files can be loaded offline.
        assertTrue(modelDirectory.isDirectory)
    }

    companion object {
        private const val BUNDLED_ZIP_ASSET = "models/moonshine_stt_tiny_en.zip"

        private val REQUIRED_FILES = listOf(
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
