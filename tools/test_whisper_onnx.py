#!/usr/bin/env python3
"""
Run the uploaded split Whisper-base ONNX model against a WAV recording.

The archive is expected to contain:
  models/whisper-base/base-encoder.int8.onnx
  models/whisper-base/base-decoder.int8.onnx
  models/whisper-base/base-tokens.txt

This runner uses Hugging Face's WhisperFeatureExtractor and WhisperTokenizer
only for preprocessing/decoding. The actual neural network inference is done
by the uploaded ONNX encoder and decoder with ONNX Runtime CPU.

Example:
  python tools/test_whisper_onnx.py .\models.zip english.wav --language en
  python tools/test_whisper_onnx.py .\models.zip hindi.wav --language hi
"""

from __future__ import annotations

import argparse
import base64
import zipfile
from pathlib import Path

import numpy as np
import onnxruntime as ort
import soundfile as sf
from transformers import WhisperFeatureExtractor, WhisperTokenizer


MODEL_ENCODER = "models/whisper-base/base-encoder.int8.onnx"
MODEL_DECODER = "models/whisper-base/base-decoder.int8.onnx"

MAX_NEW_TOKENS = 96
SAMPLE_RATE = 16000
N_LAYERS = 6
D_MODEL = 512
CACHE_LENGTH = 448
EOT_FALLBACK = 50256


def required_input(session: ort.InferenceSession, wanted: str) -> str:
    names = [x.name for x in session.get_inputs()]
    if wanted in names:
        return wanted

    normalized = wanted.replace("_", "").lower()
    for name in names:
        if name.replace("_", "").lower() == normalized:
            return name

    raise RuntimeError(f"Missing input {wanted}. Inputs: {names}")


def load_models_from_zip(zip_path: Path, work: Path) -> tuple[Path, Path]:
    work.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(zip_path) as archive:
        for name in (MODEL_ENCODER, MODEL_DECODER):
            target = work / Path(name).name
            if not target.exists():
                print("Extracting:", name)
                with archive.open(name) as source, target.open("wb") as out:
                    while True:
                        chunk = source.read(1024 * 1024)
                        if not chunk:
                            break
                        out.write(chunk)

    return work / Path(MODEL_ENCODER).name, work / Path(MODEL_DECODER).name


def read_audio(path: Path) -> np.ndarray:
    audio, sr = sf.read(str(path), dtype="float32", always_2d=False)

    if audio.ndim > 1:
        audio = np.mean(audio, axis=1)

    if sr != SAMPLE_RATE:
        raise RuntimeError(
            f"{path} is {sr} Hz. Convert it to mono 16 kHz WAV first."
        )

    if audio.size == 0:
        raise RuntimeError("Audio file is empty.")

    audio = np.nan_to_num(audio, nan=0.0, posinf=0.0, neginf=0.0)
    peak = float(np.max(np.abs(audio)))
    if peak > 1.0:
        audio = audio / peak

    return audio


def create_decoder_inputs(
    decoder: ort.InferenceSession,
    prefix_tokens: list[int],
) -> dict[str, np.ndarray]:
    inputs: dict[str, np.ndarray] = {}

    token_name = required_input(decoder, "tokens")
    self_k_name = required_input(decoder, "in_n_layer_self_k_cache")
    self_v_name = required_input(decoder, "in_n_layer_self_v_cache")
    cross_k_name = required_input(decoder, "n_layer_cross_k")
    cross_v_name = required_input(decoder, "n_layer_cross_v")
    offset_name = required_input(decoder, "offset")

    inputs[token_name] = np.asarray([prefix_tokens], dtype=np.int64)
    inputs[self_k_name] = np.zeros(
        (N_LAYERS, 1, CACHE_LENGTH, D_MODEL),
        dtype=np.float32,
    )
    inputs[self_v_name] = np.zeros(
        (N_LAYERS, 1, CACHE_LENGTH, D_MODEL),
        dtype=np.float32,
    )
    inputs[offset_name] = np.asarray([0], dtype=np.int64)

    # Filled after the encoder runs.
    inputs[cross_k_name] = None  # type: ignore[assignment]
    inputs[cross_v_name] = None  # type: ignore[assignment]
    return inputs


def decode_greedy(
    decoder: ort.InferenceSession,
    cross_k: np.ndarray,
    cross_v: np.ndarray,
    prefix_tokens: list[int],
    eos_token_id: int,
) -> list[int]:
    token_name = required_input(decoder, "tokens")
    self_k_name = required_input(decoder, "in_n_layer_self_k_cache")
    self_v_name = required_input(decoder, "in_n_layer_self_v_cache")
    cross_k_name = required_input(decoder, "n_layer_cross_k")
    cross_v_name = required_input(decoder, "n_layer_cross_v")
    offset_name = required_input(decoder, "offset")

    self_k = np.zeros(
        (N_LAYERS, 1, CACHE_LENGTH, D_MODEL),
        dtype=np.float32,
    )
    self_v = np.zeros(
        (N_LAYERS, 1, CACHE_LENGTH, D_MODEL),
        dtype=np.float32,
    )

    outputs = decoder.run(
        None,
        {
            token_name: np.asarray([prefix_tokens], dtype=np.int64),
            self_k_name: self_k,
            self_v_name: self_v,
            cross_k_name: cross_k,
            cross_v_name: cross_v,
            offset_name: np.asarray([0], dtype=np.int64),
        },
    )

    logits = outputs[0]
    next_token = int(np.argmax(logits[0, -1]))
    generated = [next_token]
    self_k = np.asarray(outputs[1], dtype=np.float32)
    self_v = np.asarray(outputs[2], dtype=np.float32)

    if next_token == eos_token_id:
        return generated

    current_offset = len(prefix_tokens)

    for _ in range(MAX_NEW_TOKENS - 1):
        outputs = decoder.run(
            None,
            {
                token_name: np.asarray([[next_token]], dtype=np.int64),
                self_k_name: self_k,
                self_v_name: self_v,
                cross_k_name: cross_k,
                cross_v_name: cross_v,
                offset_name: np.asarray([current_offset], dtype=np.int64),
            },
        )

        logits = outputs[0]
        next_token = int(np.argmax(logits[0, -1]))
        generated.append(next_token)

        self_k = np.asarray(outputs[1], dtype=np.float32)
        self_v = np.asarray(outputs[2], dtype=np.float32)
        current_offset += 1

        if next_token == eos_token_id:
            break

    return generated


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("zip_path", type=Path)
    parser.add_argument("audio_path", type=Path)
    parser.add_argument(
        "--language",
        required=True,
        choices=("en", "hi"),
    )
    args = parser.parse_args()

    if not args.zip_path.is_file():
        raise FileNotFoundError(args.zip_path)
    if not args.audio_path.is_file():
        raise FileNotFoundError(args.audio_path)

    work = Path("build/tts/whisper-test")
    encoder_path, decoder_path = load_models_from_zip(args.zip_path, work)

    print("ONNX Runtime:", ort.__version__)
    print(
        "Model weights: %.1f MiB"
        % (
            (
                encoder_path.stat().st_size
                + decoder_path.stat().st_size
            )
            / (1024 * 1024)
        )
    )

    audio = read_audio(args.audio_path)
    print("Audio:", args.audio_path)
    print("Audio duration: %.2f s" % (audio.size / SAMPLE_RATE))

    feature_extractor = WhisperFeatureExtractor.from_pretrained("openai/whisper-base")
    tokenizer = WhisperTokenizer.from_pretrained("openai/whisper-base")

    features = feature_extractor(
        audio,
        sampling_rate=SAMPLE_RATE,
        return_tensors="np",
    )["input_features"]

    # Whisper's encoder accepts [batch, 80, T]. The feature extractor normally
    # returns the 30-second padded representation expected by the base model.
    features = np.asarray(features, dtype=np.float32)

    print("Mel shape:", features.shape)

    encoder = ort.InferenceSession(
        str(encoder_path),
        providers=["CPUExecutionProvider"],
    )
    decoder = ort.InferenceSession(
        str(decoder_path),
        providers=["CPUExecutionProvider"],
    )

    mel_name = required_input(encoder, "mel")

    print("Encoder input:", encoder.get_inputs()[0].name)
    print("Encoder outputs:", [x.name for x in encoder.get_outputs()])
    print("Decoder inputs:", [x.name for x in decoder.get_inputs()])
    print("Decoder outputs:", [x.name for x in decoder.get_outputs()])

    print("Running encoder...")
    encoder_outputs = encoder.run(
        None,
        {mel_name: features},
    )

    cross_k = np.asarray(encoder_outputs[0], dtype=np.float32)
    cross_v = np.asarray(encoder_outputs[1], dtype=np.float32)

    print("Cross-K shape:", cross_k.shape)
    print("Cross-V shape:", cross_v.shape)

    tokenizer.set_prefix_tokens(
        language=args.language,
        task="transcribe",
        predict_timestamps=False,
    )

    prefix_tokens = list(tokenizer.prefix_tokens)
    eos_token_id = int(tokenizer.eos_token_id or EOT_FALLBACK)

    print("Language:", args.language)
    print("Prefix token IDs:", prefix_tokens)
    print("EOS token:", eos_token_id)
    print("Running decoder...")

    generated = decode_greedy(
        decoder=decoder,
        cross_k=cross_k,
        cross_v=cross_v,
        prefix_tokens=prefix_tokens,
        eos_token_id=eos_token_id,
    )

    all_tokens = prefix_tokens + generated
    text = tokenizer.decode(
        all_tokens,
        skip_special_tokens=True,
        normalize=False,
    ).strip()

    print("Generated token count:", len(generated))
    print("TRANSCRIPTION:")
    print(text or "<empty>")
    print("SUCCESS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
