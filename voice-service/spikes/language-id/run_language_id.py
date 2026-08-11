#!/usr/bin/env python3
"""Run a one-file spoken-language identification feasibility spike."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

import torch
from speechbrain.inference.classifiers import EncoderClassifier


MODEL_ID = "speechbrain/lang-id-voxlingua107-ecapa"
MODEL_CACHE = Path(__file__).resolve().parent / ".model-cache"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Identify the spoken language in one audio file."
    )
    parser.add_argument(
        "audio_path",
        type=Path,
        help="Path to a WAV or another audio file supported by TorchAudio.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    audio_path = args.audio_path.expanduser().resolve()

    if not audio_path.is_file():
        print(f"Audio file does not exist: {audio_path}", file=sys.stderr)
        return 2

    started_at = time.perf_counter()

    classifier = EncoderClassifier.from_hparams(
        source=MODEL_ID,
        savedir=str(MODEL_CACHE),
        run_opts={"device": "cpu"},
    )

    with torch.inference_mode():
        _, log_score, class_index, labels = classifier.classify_file(
            str(audio_path)
        )

    result = {
        "audio_path": str(audio_path),
        "model_id": MODEL_ID,
        "predicted_language": labels[0],
        "class_index": int(class_index.squeeze().item()),
        "log_score": float(log_score.squeeze().item()),
        "confidence": float(log_score.exp().squeeze().item()),
        "elapsed_seconds": round(time.perf_counter() - started_at, 3),
    }

    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
