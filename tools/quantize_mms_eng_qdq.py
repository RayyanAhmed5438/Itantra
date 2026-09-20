#!/usr/bin/env python3
"""
Create a mobile-friendly INT8 QDQ ONNX model for the English MMS VITS TTS model.

The script:
  1. downloads the FP32 ONNX model from Xenova/mms-tts-eng,
  2. downloads its tokenizer,
  3. calibrates on representative English sentences,
  4. quantizes with ONNX Runtime using S8S8 + QDQ,
  5. verifies that the result contains no ConvInteger nodes,
  6. writes the result to the requested output path.

Use Python 3.11/3.12 in a fresh virtual environment for the most
predictable ONNX Runtime tooling setup.

The output model is intentionally NOT committed by this script.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import numpy as np
import onnx
import onnxruntime as ort
from huggingface_hub import hf_hub_download
from onnxruntime.quantization import (
    CalibrationDataReader,
    CalibrationMethod,
    QuantFormat,
    QuantType,
    quantize_static,
)
from tokenizers import Tokenizer


MODEL_REPO = "Xenova/mms-tts-eng"
MODEL_FILE = "onnx/model.onnx"
TOKENIZER_FILE = "tokenizer.json"

CALIBRATION_TEXTS = [
    "Hello, how are you today?",
    "This is a short test sentence for English speech synthesis.",
    "The quick brown fox jumps over the lazy dog.",
    "Itantra is an offline tactical communication system.",
    "Please send the message when you are ready.",
    "The team is moving to the next checkpoint.",
    "Communication should continue even without internet access.",
    "This message is being generated locally on the device.",
    "Good morning. The system is ready.",
    "Stay alert and report any changes.",
    "One two three four five six seven eight nine ten.",
    "We need a clear voice with low device resource usage.",
]


def resolve_input_name(session: ort.InferenceSession, wanted: str) -> str | None:
    for name in session.get_inputs():
        if name.name == wanted:
            return name.name

    normalized = wanted.replace("_", "").lower()
    for name in session.get_inputs():
        if name.name.replace("_", "").lower() == normalized:
            return name.name

    return None


class MmsCalibrationReader(CalibrationDataReader):
    def __init__(self, model_path: Path, tokenizer_path: Path) -> None:
        self.session = ort.InferenceSession(
            str(model_path),
            providers=["CPUExecutionProvider"],
        )

        self.tokenizer = Tokenizer.from_file(str(tokenizer_path))
        self.input_names = [x.name for x in self.session.get_inputs()]

        self.input_ids_name = resolve_input_name(self.session, "input_ids")
        self.attention_mask_name = resolve_input_name(
            self.session, "attention_mask"
        )

        if self.input_ids_name is None:
            raise RuntimeError(
                "Expected an input_ids input. Model inputs: "
                + str(self.input_names)
            )

        self.samples: list[dict[str, np.ndarray]] = []
        for text in CALIBRATION_TEXTS:
            encoded = self.tokenizer.encode(text)

            ids = np.asarray(
                [encoded.ids],
                dtype=np.int64,
            )

            sample: dict[str, np.ndarray] = {
                self.input_ids_name: ids,
            }

            if self.attention_mask_name is not None:
                sample[self.attention_mask_name] = np.ones(
                    ids.shape,
                    dtype=np.int64,
                )

            # This export normally has only input_ids and attention_mask.
            # Fail clearly instead of silently feeding the wrong type if
            # a future model revision introduces another required input.
            known = {
                self.input_ids_name,
                self.attention_mask_name,
            }
            unknown_inputs = [
                name for name in self.input_names if name not in known
            ]
            if unknown_inputs:
                raise RuntimeError(
                    "Calibration model has unsupported extra inputs: "
                    + str(unknown_inputs)
                )

            self.samples.append(sample)

        self._index = 0

    def get_next(self) -> dict[str, np.ndarray] | None:
        if self._index >= len(self.samples):
            return None

        sample = self.samples[self._index]
        self._index += 1
        return sample

    def rewind(self) -> None:
        self._index = 0


def inspect_model(path: Path) -> dict[str, Any]:
    model = onnx.load(str(path), load_external_data=True)

    op_counts: dict[str, int] = {}
    conv_integer_nodes: list[str] = []

    for node in model.graph.node:
        op_counts[node.op_type] = op_counts.get(node.op_type, 0) + 1
        if node.op_type == "ConvInteger":
            conv_integer_nodes.append(node.name or "<unnamed>")

    inputs = [
        {
            "name": x.name,
            "dtype": x.type.tensor_type.elem_type,
        }
        for x in model.graph.input
    ]

    outputs = [
        {
            "name": x.name,
            "dtype": x.type.tensor_type.elem_type,
        }
        for x in model.graph.output
    ]

    return {
        "ir_version": model.ir_version,
        "opset": [
            imp.version
            for imp in model.opset_import
            if imp.domain in ("", "ai.onnx")
        ],
        "inputs": inputs,
        "outputs": outputs,
        "op_counts": dict(sorted(op_counts.items())),
        "conv_integer_nodes": conv_integer_nodes,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/tts/mms-tts-eng-qdq/model.int8.onnx"),
        help="Output ONNX path.",
    )
    args = parser.parse_args()

    output = args.output
    # Correct a typo-safe default if this script is invoked without --output.
    if "q8-q dq" in str(output):
        output = Path("build/tts/mms-tts-eng-qdq/model.int8.onnx")

    work = Path("build/tts/mms-tts-eng-qdq")
    work.mkdir(parents=True, exist_ok=True)

    print("ONNX Runtime:", ort.__version__)
    print("Downloading English MMS FP32 model...")

    model_path = Path(
        hf_hub_download(
            repo_id=MODEL_REPO,
            filename=MODEL_FILE,
            local_dir=str(work / "source"),
        )
    )

    tokenizer_path = Path(
        hf_hub_download(
            repo_id=MODEL_REPO,
            filename=TOKENIZER_FILE,
            local_dir=str(work / "source"),
        )
    )

    print("FP32 model:", model_path)
    print("FP32 size: %.1f MiB" % (model_path.stat().st_size / (1024 * 1024)))

    before = inspect_model(model_path)
    print("Source inputs:", before["inputs"])
    print("Source outputs:", before["outputs"])
    print("Source ConvInteger nodes:", len(before["conv_integer_nodes"]))

    reader = MmsCalibrationReader(model_path, tokenizer_path)

    output.parent.mkdir(parents=True, exist_ok=True)

    print("Quantizing with S8S8 + QDQ...")
    quantize_static(
        model_input=str(model_path),
        model_output=str(output),
        calibration_data_reader=reader,
        quant_format=QuantFormat.QDQ,
        activation_type=QuantType.QInt8,
        weight_type=QuantType.QInt8,
        per_channel=False,
        calibrate_method=CalibrationMethod.MinMax,
        extra_options={
            # Keep the graph in the QDQ representation; do not turn Conv
            # back into ConvInteger.
            "DedicatedQDQPair": False,
            "MatMulConstBOnly": True,
        },
    )

    after = inspect_model(output)

    print()
    print("QDQ model:", output)
    print("QDQ size: %.1f MiB" % (output.stat().st_size / (1024 * 1024)))
    print("QDQ inputs:", after["inputs"])
    print("QDQ outputs:", after["outputs"])
    print("QDQ ConvInteger nodes:", len(after["conv_integer_nodes"]))

    if after["conv_integer_nodes"]:
        print(
            "ERROR: ConvInteger nodes remain in the quantized graph:",
            after["conv_integer_nodes"],
            file=sys.stderr,
        )
        return 2

    # Basic CPU session validation catches unsupported operator/type issues
    # before the model is copied into the Android APK.
    print("Opening the quantized model with CPUExecutionProvider...")
    session = ort.InferenceSession(
        str(output),
        providers=["CPUExecutionProvider"],
    )

    print("CPU session created successfully.")
    print("Inputs:", [x.name for x in session.get_inputs()])
    print("Outputs:", [x.name for x in session.get_outputs()])

    metadata = {
        "source_repo": MODEL_REPO,
        "source_file": MODEL_FILE,
        "quant_format": "QDQ",
        "activation_type": "QInt8",
        "weight_type": "QInt8",
        "per_channel": False,
        "calibration_method": "MinMax",
        "source": before,
        "quantized": after,
    }

    meta_path = output.with_suffix(".metadata.json")
    meta_path.write_text(
        json.dumps(metadata, indent=2),
        encoding="utf-8",
    )

    print("Metadata:", meta_path)
    print("SUCCESS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
