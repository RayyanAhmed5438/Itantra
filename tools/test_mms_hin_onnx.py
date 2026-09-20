#!/usr/bin/env python3
"""Run the official quantized Hindi MMS VITS ONNX model with the official tokenizer."""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import onnxruntime as ort
import soundfile as sf
from huggingface_hub import hf_hub_download
from transformers import VitsTokenizer


MODEL_REPO = "Xenova/mms-tts-hin"
TOKENIZER_REPO = "facebook/mms-tts-hin"
TEXT = "नमस्ते। यह डिवाइस पर हिंदी वाक् संश्लेषण का परीक्षण है।"


def resolve(session: ort.InferenceSession, wanted: str) -> str:
    names = [x.name for x in session.get_inputs()]
    exact = next((n for n in names if n == wanted), None)
    if exact:
        return exact

    normalized = wanted.replace("_", "").lower()
    fuzzy = next(
        (n for n in names if n.replace("_", "").lower() == normalized),
        None,
    )
    if fuzzy:
        return fuzzy

    raise RuntimeError(f"Missing {wanted}. Inputs: {names}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, default=None)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/tts/mms-tts-hin-official-quantized.wav"),
    )
    args = parser.parse_args()

    model_path = args.model
    if model_path is None:
        model_path = Path(
            hf_hub_download(
                repo_id=MODEL_REPO,
                filename="onnx/model_quantized.onnx",
            )
        )

    print("Model:", model_path)
    print(
        "Model size: %.1f MiB"
        % (model_path.stat().st_size / (1024 * 1024))
    )

    tokenizer = VitsTokenizer.from_pretrained(TOKENIZER_REPO)
    encoded = tokenizer(TEXT, return_tensors="np")

    input_ids = np.asarray(encoded["input_ids"], dtype=np.int64)
    attention_mask = np.asarray(
        encoded["attention_mask"],
        dtype=np.int64,
    )

    print("Token count:", int(input_ids.shape[-1]))
    print("Tokenizer input_ids shape:", input_ids.shape)
    print("Tokenizer attention_mask shape:", attention_mask.shape)

    session = ort.InferenceSession(
        str(model_path),
        providers=["CPUExecutionProvider"],
    )

    input_ids_name = resolve(session, "input_ids")
    attention_mask_name = resolve(session, "attention_mask")

    print("ORT inputs:", [x.name for x in session.get_inputs()])
    print("ORT outputs:", [x.name for x in session.get_outputs()])

    outputs = session.run(
        None,
        {
            input_ids_name: input_ids,
            attention_mask_name: attention_mask,
        },
    )

    waveform = np.asarray(outputs[0], dtype=np.float32).squeeze()
    waveform = waveform.reshape(-1)

    if waveform.size == 0:
        raise RuntimeError("The model returned an empty waveform.")
    if not np.isfinite(waveform).all():
        raise RuntimeError("The waveform contains NaN or infinity.")

    peak = float(np.max(np.abs(waveform)))
    rms = float(np.sqrt(np.mean(np.square(waveform))))
    duration = waveform.size / 16000.0

    print("Waveform samples:", waveform.size)
    print("Waveform duration: %.3f s" % duration)
    print("Waveform min/max: %.6f / %.6f" % (
        float(waveform.min()),
        float(waveform.max()),
    ))
    print("Waveform peak: %.6f" % peak)
    print("Waveform RMS: %.6f" % rms)

    if peak <= 0.0 or rms <= 0.0:
        raise RuntimeError("Waveform appears to be silent.")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    sf.write(
        str(args.output),
        np.clip(waveform, -1.0, 1.0),
        16000,
        subtype="PCM_16",
    )

    print("WAV:", args.output.resolve())
    print("SUCCESS")


if __name__ == "__main__":
    raise SystemExit(main())
