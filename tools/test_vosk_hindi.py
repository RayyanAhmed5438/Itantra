#!/usr/bin/env python3
"""
Test the Vosk small Hindi STT model against a WAV file.

Expected model archive:
  app/src/main/assets/models/vosk-model-small-hi-0.22.zip

Expected WAV:
  hindi.wav

The archive is extracted to:
  build/vosk/hindi/

Install:
  pip install vosk

Examples:
  python tools/test_vosk_hindi.py
  python tools/test_vosk_hindi.py .\\hindi.wav
  python tools/test_vosk_hindi.py .\\hindi.wav --chunk-ms 100
"""

from __future__ import annotations

import argparse
import json
import time
import wave
import zipfile
from pathlib import Path

from vosk import KaldiRecognizer, Model


DEFAULT_MODEL_ZIP = Path(
    "app/src/main/assets/models/vosk-model-small-hi-0.22.zip"
)
DEFAULT_AUDIO = Path("hindi.wav")
WORK_DIR = Path("build/vosk/hindi")

REQUIRED_MODEL_FILES = {
    "README",
    "am/final.mdl",
    "conf/model.conf",
    "conf/mfcc.conf",
    "graph/Gr.fst",
    "graph/HCLr.fst",
    "graph/disambig_tid.int",
    "graph/phones/word_boundary.int",
    "ivector/final.dubm",
    "ivector/final.ie",
    "ivector/final.mat",
    "ivector/global_cmvn.stats",
    "ivector/online_cmvn.conf",
    "ivector/splice.conf",
}

SAMPLE_RATE = 16000


def extract_model(zip_path: Path, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(zip_path) as archive:
        files = [
            name.replace("\\\\", "/")
            for name in archive.namelist()
            if not name.endswith("/")
        ]

        relative_names: list[tuple[str, str]] = []
        for name in files:
            parts = Path(name).parts
            relative = "/".join(parts[1:]) if len(parts) > 1 else name
            relative_names.append((name, relative))

        available = {relative for _, relative in relative_names}
        missing = sorted(REQUIRED_MODEL_FILES - available)
        if missing:
            raise RuntimeError(
                "Vosk Hindi model archive is missing: " + ", ".join(missing)
            )

        for archive_name, relative in relative_names:
            if relative not in REQUIRED_MODEL_FILES:
                continue

            target = output_dir / relative
            resolved = target.resolve()
            root = output_dir.resolve()

            if root not in resolved.parents and resolved != root:
                raise RuntimeError(
                    f"Unsafe path in model archive: {archive_name}"
                )

            target.parent.mkdir(parents=True, exist_ok=True)

            if not target.exists():
                print("Extracting:", archive_name)
                with archive.open(archive_name) as source, target.open("wb") as destination:
                    while True:
                        chunk = source.read(1024 * 1024)
                        if not chunk:
                            break
                        destination.write(chunk)

    return output_dir


def read_audio(audio_path: Path) -> tuple[wave.Wave_read, int]:
    wav = wave.open(str(audio_path), "rb")

    channels = wav.getnchannels()
    sample_width = wav.getsampwidth()
    sample_rate = wav.getframerate()
    compression = wav.getcomptype()

    if channels != 1:
        wav.close()
        raise RuntimeError(
            f"{audio_path} has {channels} channels. "
            "Convert it to mono 16-bit PCM WAV first."
        )

    if sample_width != 2:
        wav.close()
        raise RuntimeError(
            f"{audio_path} has {sample_width * 8}-bit samples. "
            "Convert it to 16-bit PCM WAV first."
        )

    if sample_rate != SAMPLE_RATE:
        wav.close()
        raise RuntimeError(
            f"{audio_path} is {sample_rate} Hz. "
            f"Convert it to mono {SAMPLE_RATE} Hz WAV first."
        )

    if compression != "NONE":
        wav.close()
        raise RuntimeError(
            f"{audio_path} uses compressed WAV audio ({compression}). "
            "Use uncompressed PCM WAV."
        )

    return wav, wav.getnframes()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "audio_path",
        nargs="?",
        type=Path,
        default=DEFAULT_AUDIO,
        help=f"WAV file to transcribe (default: {DEFAULT_AUDIO})",
    )
    parser.add_argument(
        "--model-zip",
        type=Path,
        default=DEFAULT_MODEL_ZIP,
        help=f"Vosk model ZIP (default: {DEFAULT_MODEL_ZIP})",
    )
    parser.add_argument(
        "--chunk-ms",
        type=int,
        default=100,
        help="Audio chunk size for streaming simulation (default: 100 ms)",
    )
    args = parser.parse_args()

    if args.chunk_ms <= 0:
        raise ValueError("--chunk-ms must be > 0")
    if not args.model_zip.is_file():
        raise FileNotFoundError(f"Model ZIP not found: {args.model_zip}")
    if not args.audio_path.is_file():
        raise FileNotFoundError(f"Audio file not found: {args.audio_path}")

    model_dir = extract_model(args.model_zip, WORK_DIR)
    wav, frame_count = read_audio(args.audio_path)

    duration = frame_count / SAMPLE_RATE

    print("Vosk Python Hindi STT test")
    print("Model:", model_dir)
    print("Audio:", args.audio_path)
    print(f"Sample rate: {SAMPLE_RATE} Hz")
    print("Channels: 1")
    print("Format: 16-bit PCM")
    print(f"Duration: {duration:.2f} s")
    print(f"Chunk: {args.chunk_ms} ms")
    print()

    print("Loading Vosk model...")
    model_load_start = time.perf_counter()
    model = Model(str(model_dir))
    model_load_ms = (time.perf_counter() - model_load_start) * 1000
    print(f"Model load: {model_load_ms:.0f} ms")

    recognizer = KaldiRecognizer(model, SAMPLE_RATE)
    recognizer.SetWords(True)

    chunk_frames = max(1, int(SAMPLE_RATE * args.chunk_ms / 1000))
    partial_last = ""
    final_segments: list[str] = []
    accepted_audio_seconds = 0.0
    stream_start = time.perf_counter()

    print()
    print("=== STREAMING ===")

    try:
        while True:
            data = wav.readframes(chunk_frames)
            if not data:
                break

            chunk_duration = len(data) / (SAMPLE_RATE * 2)
            accepted_audio_seconds += chunk_duration

            if recognizer.AcceptWaveform(data):
                result = json.loads(recognizer.Result())
                text_value = (result.get("text") or "").strip()
                if text_value:
                    final_segments.append(text_value)
                    print(f"FINAL:   {text_value}")
            else:
                partial = json.loads(recognizer.PartialResult()).get("partial", "").strip()
                if partial and partial != partial_last:
                    print(f"PARTIAL: {partial}")
                    partial_last = partial

    finally:
        wav.close()

    final_result = json.loads(recognizer.FinalResult())
    final_tail = (final_result.get("text") or "").strip()
    if final_tail:
        final_segments.append(final_tail)

    final_text = " ".join(final_segments).strip()
    elapsed_ms = (time.perf_counter() - stream_start) * 1000

    print()
    print("=== TRANSCRIPTION ===")
    print(final_text or "<empty>")

    print()
    print("=== TIMING ===")
    print(f"Model load: {model_load_ms:.0f} ms")
    print(f"STT processing: {elapsed_ms:.0f} ms")
    print(f"Audio duration: {duration:.2f} s")
    if duration > 0:
        print(f"Real-time factor: {elapsed_ms / 1000 / duration:.3f}x")

    print()
    print("SUCCESS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
