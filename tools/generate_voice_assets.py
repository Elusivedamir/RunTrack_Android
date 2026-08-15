#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import io
import json
import wave
import zipfile
from pathlib import Path

MODEL_REVISION = "a31bce3ed50c05399b2a830efd1c607df03cf4b5"
MODEL_SHA256 = "f073356ebc4bd0f80c5af58df2953a5988bd5bdab1eb38635ce960b071fbefcb"
SAMPLE_RATE = 22_050
CHANNELS = 1
SAMPLE_WIDTH = 2

TOKEN_TEXT = {
    "start": "Старт",
    "one": "один",
    "two": "два",
    "three": "три",
    "four": "четыре",
    "five": "пять",
    "six": "шесть",
    "seven": "семь",
    "eight": "восемь",
    "nine": "девять",
    "ten": "десять",
    "eleven": "одиннадцать",
    "twelve": "двенадцать",
    "thirteen": "тринадцать",
    "fourteen": "четырнадцать",
    "fifteen": "пятнадцать",
    "sixteen": "шестнадцать",
    "seventeen": "семнадцать",
    "eighteen": "восемнадцать",
    "nineteen": "девятнадцать",
    "twenty": "двадцать",
    "thirty": "тридцать",
    "forty": "сорок",
    "fifty": "пятьдесят",
    "sixty": "шестьдесят",
    "seventy": "семьдесят",
    "eighty": "восемьдесят",
    "ninety": "девяносто",
    "one_hundred": "сто",
    "two_hundred": "двести",
    "three_hundred": "триста",
    "four_hundred": "четыреста",
    "five_hundred": "пятьсот",
    "six_hundred": "шестьсот",
    "seven_hundred": "семьсот",
    "eight_hundred": "восемьсот",
    "nine_hundred": "девятьсот",
    "one_feminine": "одна",
    "two_feminine": "две",
    "thousand_one": "тысяча",
    "thousand_few": "тысячи",
    "thousand_many": "тысяч",
    "million_one": "миллион",
    "million_few": "миллиона",
    "million_many": "миллионов",
    "billion_one": "миллиард",
    "billion_few": "миллиарда",
    "billion_many": "миллиардов",
    "kilometer_one": "километр",
    "kilometer_few": "километра",
    "kilometer_many": "километров",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def synthesize(model: Path, config: Path, output: Path) -> None:
    if sha256(model) != MODEL_SHA256:
        raise SystemExit("Piper model SHA256 mismatch")

    from piper import PiperVoice

    voice = PiperVoice.load(str(model), config_path=str(config))
    output.mkdir(parents=True, exist_ok=True)

    expected_files = {f"{token}.pcm" for token in TOKEN_TEXT}
    for stale in output.glob("*.pcm"):
        if stale.name not in expected_files:
            stale.unlink()

    for token, text in TOKEN_TEXT.items():
        memory = io.BytesIO()
        with wave.open(memory, "wb") as wav_out:
            voice.synthesize_wav(text, wav_out)

        memory.seek(0)
        with wave.open(memory, "rb") as wav_in:
            if wav_in.getframerate() != SAMPLE_RATE:
                raise RuntimeError(
                    f"{token}: expected {SAMPLE_RATE} Hz, got {wav_in.getframerate()}"
                )
            if wav_in.getnchannels() != CHANNELS:
                raise RuntimeError(
                    f"{token}: expected mono, got {wav_in.getnchannels()} channels"
                )
            if wav_in.getsampwidth() != SAMPLE_WIDTH:
                raise RuntimeError(
                    f"{token}: expected 16-bit PCM, got {wav_in.getsampwidth() * 8}-bit"
                )
            if wav_in.getcomptype() != "NONE":
                raise RuntimeError(f"{token}: compressed WAV is not supported")
            pcm = wav_in.readframes(wav_in.getnframes())

        if len(pcm) < SAMPLE_RATE * SAMPLE_WIDTH // 20:
            raise RuntimeError(f"{token}: generated clip is unexpectedly short")
        if len(pcm) % SAMPLE_WIDTH != 0:
            raise RuntimeError(f"{token}: PCM byte count is not sample-aligned")

        target = output / f"{token}.pcm"
        temp = target.with_suffix(".pcm.tmp")
        temp.write_bytes(pcm)
        temp.replace(target)

    manifest = {
        "format": "pcm_s16le",
        "sample_rate": SAMPLE_RATE,
        "channels": CHANNELS,
        "sample_width": SAMPLE_WIDTH,
        "model": "ru_RU-dmitri-medium",
        "model_revision": MODEL_REVISION,
        "model_sha256": MODEL_SHA256,
        "tokens": sorted(TOKEN_TEXT),
    }
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
        encoding="utf-8",
    )
    verify_directory(output)


def verify_directory(output: Path) -> None:
    manifest_path = output / "manifest.json"
    if not manifest_path.is_file():
        raise SystemExit("voice manifest is missing")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("sample_rate") != SAMPLE_RATE:
        raise SystemExit("voice manifest sample rate mismatch")
    if manifest.get("model_sha256") != MODEL_SHA256:
        raise SystemExit("voice manifest model hash mismatch")
    if set(manifest.get("tokens", [])) != set(TOKEN_TEXT):
        raise SystemExit("voice manifest token set mismatch")

    for token in TOKEN_TEXT:
        path = output / f"{token}.pcm"
        if not path.is_file() or path.stat().st_size < 100:
            raise SystemExit(f"voice asset missing or empty: {path}")


def verify_apk(apk: Path) -> None:
    if not apk.is_file():
        raise SystemExit(f"APK not found: {apk}")

    prefix = "assets/voice_ru/"
    with zipfile.ZipFile(apk, "r") as archive:
        names = set(archive.namelist())
        forbidden = [
            name
            for name in names
            if name.lower().endswith((".onnx", ".onnx.json"))
            or "piper-voices" in name.lower()
        ]
        if forbidden:
            raise SystemExit(
                "Build-time TTS model leaked into APK: " + ", ".join(sorted(forbidden))
            )

        manifest_name = prefix + "manifest.json"
        if manifest_name not in names:
            raise SystemExit("Offline voice manifest is missing from APK")
        manifest = json.loads(archive.read(manifest_name).decode("utf-8"))
        if manifest.get("sample_rate") != SAMPLE_RATE:
            raise SystemExit("APK voice sample rate mismatch")
        if manifest.get("model_sha256") != MODEL_SHA256:
            raise SystemExit("APK voice model hash mismatch")
        if set(manifest.get("tokens", [])) != set(TOKEN_TEXT):
            raise SystemExit("APK voice token set mismatch")

        for token in TOKEN_TEXT:
            name = prefix + token + ".pcm"
            if name not in names:
                raise SystemExit(f"APK voice asset missing: {name}")
            if archive.getinfo(name).file_size < 100:
                raise SystemExit(f"APK voice asset empty: {name}")

    print(f"OK: APK contains {len(TOKEN_TEXT)} offline Russian voice tokens")
    print("OK: no Piper ONNX model is packaged in the APK")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path)
    parser.add_argument("--config", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--verify-apk", type=Path)
    args = parser.parse_args()

    generate_args = (args.model, args.config, args.output)
    if args.verify_apk is not None:
        if any(value is not None for value in generate_args):
            parser.error("--verify-apk cannot be combined with generation arguments")
    elif any(value is None for value in generate_args):
        parser.error("--model, --config and --output are required for generation")
    return args


def main() -> None:
    args = parse_args()
    if args.verify_apk is not None:
        verify_apk(args.verify_apk)
        return
    synthesize(args.model, args.config, args.output)
    print(f"OK: generated {len(TOKEN_TEXT)} offline voice tokens in {args.output}")


if __name__ == "__main__":
    main()
