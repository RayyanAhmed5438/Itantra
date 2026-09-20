#!/usr/bin/env python3
"""Inspect the uploaded Whisper encoder/decoder ONNX models."""

from __future__ import annotations

import argparse
import zipfile
from pathlib import Path

import onnxruntime as ort


def print_session(label: str, path: Path) -> None:
    print("\n===" + label + "===")
    print("FILE:", path)
    print("SIZE: %.1f MiB" % (path.stat().st_size / (1024 * 1024)))

    session = ort.InferenceSession(
        str(path),
        providers=["CPUExecutionProvider"],
    )

    print("INPUTS:")
    for item in session.get_inputs():
        print("  ", item.name, item.type, item.shape)

    print("OUTPUTS:")
    for item in session.get_outputs():
        print("  ", item.name, item.type, item.shape)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "zip_path",
        type=Path,
        help="Path to models.zip containing models/whisper-base/",
    )
    args = parser.parse_args()

    if not args.zip_path.is_file():
        raise FileNotFoundError(args.zip_path)

    work = Path("build/tts/whisper-inspect")
    work.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(args.zip_path) as archive:
        wanted = {
            "models/whisper-base/base-encoder.int8.onnx",
            "models/whisper-base/base-decoder.int8.onnx",
            "models/whisper-base/base-tokens.txt",
        }

        missing = sorted(wanted - set(archive.namelist()))
        if missing:
            raise RuntimeError("Missing archive entries: " + str(missing))

        for name in wanted:
            target = work / Path(name).name
            with archive.open(name) as source, target.open("wb") as destination:
                destination.write(source.read())

    print("ONNX Runtime:", ort.__version__)
    print_session(
        "ENCODER",
        work / "base-encoder.int8.onnx",
    )
    print_session(
        "DECODER",
        work / "base-decoder.int8.onnx",
    )

    token_file = work / "base-tokens.txt"
    lines = token_file.read_text(encoding="utf-8").splitlines()
    print("\nTOKENS:", len(lines))
    print("FIRST:", lines[:5])
    print("LAST:", lines[-5:])

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
