import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
    base
}

group = "com.tactical.moonshine"
version = libs.versions.moonshineVoice.get()

abstract class PatchMoonshineAarTask : DefaultTask() {
    @get:InputFiles
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun patch() {
        val source = sourceFiles.singleFile
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()

        ZipInputStream(
            BufferedInputStream(source.inputStream())
        ).use { input ->
            ZipOutputStream(
                BufferedOutputStream(target.outputStream())
            ).use { out ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val normalized = entry.name.replace('\\', '/')
                    val isMoonshineBundledOrt =
                        normalized.startsWith("jni/") &&
                            normalized.substringAfterLast('/') == "libonnxruntime.so"

                    if (!isMoonshineBundledOrt) {
                        val copied = ZipEntry(normalized)
                        if (entry.time >= 0L) {
                            copied.time = entry.time
                        }
                        out.putNextEntry(copied)
                        if (!entry.isDirectory) {
                            input.copyTo(out, 64 * 1024)
                        }
                        out.closeEntry()
                    }

                    input.closeEntry()
                }
            }
        }
    }
}

val moonshineSource = configurations.create("moonshineSource") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    add(
        "moonshineSource",
        "ai.moonshine:moonshine-voice:" + libs.versions.moonshineVoice.get()
    )
}

val patchMoonshineAar = tasks.register<PatchMoonshineAarTask>("patchMoonshineAar") {
    sourceFiles.from(moonshineSource)
    outputFile.set(
        layout.buildDirectory.file(
            "outputs/moonshine-voice-" +
                libs.versions.moonshineVoice.get() +
                "-no-ort.aar"
        )
    )
}

configurations.named("default") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("default", patchMoonshineAar.flatMap { it.outputFile }) {
        type = "aar"
        builtBy(patchMoonshineAar)
    }
}
