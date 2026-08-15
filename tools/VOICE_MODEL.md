# RunTrack offline Russian voice source

RunTrack generates its Russian workout prompts during GitHub Actions and packages only the
resulting PCM audio assets into the APK. The Piper model and Python package are build-time-only.

- Generator: `piper-tts==1.4.2`
- Voice: `ru_RU-dmitri-medium`
- Voice repository revision: `a31bce3ed50c05399b2a830efd1c607df03cf4b5`
- ONNX SHA256: `f073356ebc4bd0f80c5af58df2953a5988bd5bdab1eb38635ce960b071fbefcb`
- Sample rate: 22,050 Hz
- Voice path: `rhasspy/piper-voices/ru/ru_RU/dmitri/medium`
- The model card identifies the Ruslan dataset and its CC0 license.
- The `rhasspy/piper-voices` repository is published with an MIT repository license.

The build verifies the ONNX SHA256 before synthesis and verifies that no `.onnx` model is present
inside the final APK.
