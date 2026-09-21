#!/usr/bin/env python3
"""
Test the bundled Moonshine Tiny Streaming English STT model against a WAV file.

Expected model archive:
  app/src/main/assets/models/moonshine_stt_tiny_en.zip

Expected WAV:
  english.wav

The archive may contain a single top-level folder. The script extracts it to:
  build/moonshine/stt-tiny-en/

Install:
  pip install moonshine-voice soundfile

Example:
  python tools/test_moonshine.py
  python tools/test_moonshine.py .\\english.wav
  python tools/test_moonshine.py .\\english.wav --chunk-ms 100
"""

from __future__ import annotations

import argparse
import zipfile
from pathlib import Path

import soundfile as sf
from moonshine_voice import ModelArch, Transcriber


DEFAULT_MODEL_ZIP = Path(
    "app/src/main/assets/models/moonshine_stt_tiny_en.zip"
)
DEFAULT_AUDIO = Path("english.wav")
WORK_DIR = Path("build/moonshine/stt-tiny-en")

REQUIRED_MODEL_FILES = {
    "adapter.ort",
    "cross_kv.ort",
    "decoder_kv.ort",
    "encoder.ort",
    "frontend.model.ort",
    "frontend.weights.ort",
    "streaming_config.json",
    "tokenizer.bin",
}

SAMPLE_RATE = 16000


def extract_model(zip_path: Path, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(zip_path) as archive:
        names = [
            name.replace("\\\\", "/")
            for name in archive.namelist()
            if not name.endswith("/")
        ]

        # Accept either:
        #   file.ort
        # or:
        #   moonshine_stt_tiny_en/file.ort
        relative_names: list[tuple[str, str]] = []
        for name in names:
            parts = Path(name).parts
            relative = "/".join(parts[1:]) if len(parts) > 1 else name
            relative_names.append((name, relative))

        available = {relative for _, relative in relative_names}
        missing = sorted(REQUIRED_MODEL_FILES - available)
        if missing:
            raise RuntimeError(
                "Moonshine model archive is missing: " + ", ".join(missing)
            )

        for archive_name, relative in relative_names:
            if relative not in REQUIRED_MODEL_FILES:
                continue

            target = output_dir / relative
            target.parent.mkdir(parents=True, exist_ok=True)

            # Avoid ZIP path traversal.
            resolved = target.resolve()
            root = output_dir.resolve()
            if root not in resolved.parents and resolved != root:
                raise RuntimeError(
                    f"Unsafe path in model archive: {archive_name}"
                )

            if not target.exists():
                print("Extracting:", archive_name)
                with archive.open(archive_name) as source, target.open("wb") as destination:
                    while True:
                        chunk = source.read(1024 * 1024)
                        if not chunk:
                            break
                        destination.write(chunk)

    return output_dir


def read_audio(audio_path: Path) -> tuple[list[float], int]:
    audio, sample_rate = sf.read(
        str(audio_path),
        dtype="float32",
        always_2d=False,
    )

    if audio.ndim > 1:
        audio = audio.mean(axis=1)

    if audio.size == 0:
        raise RuntimeError("Audio file is empty.")

    if sample_rate != SAMPLE_RATE:
        raise RuntimeError(
            f"{audio_path} is {sample_rate} Hz. "
            f"Convert it to mono {SAMPLE_RATE} Hz WAV first."
        )

    return audio.astype("float32").tolist(), sample_rate


class PrintListener:
    """Print Moonshine streaming updates in the same style as the Android test."""

    def on_line_started(self, event) -> None:
        print(f"LINE STARTED: {event.line.text}")

    def on_line_text_changed(self, event) -> None:
        print(f"PARTIAL: {event.line.text}")

    def on_line_completed(self, event) -> None:
        print(f"FINAL:   {event.line.text}")


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
        help=f"Moonshine model ZIP (default: {DEFAULT_MODEL_ZIP})",
    )
    parser.add_argument(
        "--chunk-ms",
        type=int,
        default=100,
        help="Audio chunk size for streaming simulation (default: 100 ms)",
    )
    parser.add_argument(
        "--update-interval",
        type=float,
        default=0.5,
        help="Moonshine streaming update interval in seconds (default: 0.5)",
    )
    args = parser.parse_args()

    if args.chunk_ms <= 0:
        raise ValueError("--chunk-ms must be > 0")
    if not args.model_zip.is_file():
        raise FileNotFoundError(f"Model ZIP not found: {args.model_zip}")
    if not args.audio_path.is_file():
        raise FileNotFoundError(f"Audio file not found: {args.audio_path}")

    model_dir = extract_model(args.model_zip, WORK_DIR)
    audio, sample_rate = read_audio(args.audio_path)

    print("Moonshine Python STT test")
    print("Model:", model_dir)
    print("Architecture:", ModelArch.TINY_STREAMING.name)
    print("Audio:", args.audio_path)
    print(f"Sample rate: {sample_rate} Hz")
    print(f"Duration: {len(audio) / sample_rate:.2f} s")
    print(f"Chunk: {args.chunk_ms} ms")
    print(f"Update interval: {args.update_interval:.2f} s")
    print()

    transcriber = Transcriber(
        model_path=model_dir,
        model_arch=ModelArch.TINY_STREAMING,
        update_interval=args.update_interval,
    )
    listener = PrintListener()
    transcriber.add_listener(listener)

    chunk_size = max(1, int(sample_rate * args.chunk_ms / 1000))

    try:
        stream = transcriber.create_stream(
            update_interval=args.update_interval,
        )

        print("=== STREAMING ===")
        stream.start()

        for start in range(0, len(audio), chunk_size):
            chunk = audio[start : start + chunk_size]
            stream.add_audio(chunk, sample_rate)

        transcript = stream.stop()

        print()
        print("=== TRANSCRIPTION ===")

        final_lines: list[str] = []
        if transcript is not None:
            for line in transcript.lines:
                text = (line.text or "").strip()
                if text:
                    final_lines.append(text)

        final_text = "\n".join(final_lines).strip()
        print(final_text or "<empty>")

        print()
        print("SUCCESS")
        return 0
    finally:
        transcriber.close()


if __name__ == "__main__":
    raise SystemExit(main())
