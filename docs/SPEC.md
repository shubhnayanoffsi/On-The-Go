# On The Go - locked spec (v0.1)

## Goal
Pure native Android APK. No Termux. No terminal. Install and it sets itself up.
Device: Redmi Pad Pro, Snapdragon 7s Gen 2, 6GB, arm64-v8a. English only.

## Pipeline
AudioRecord 16kHz mono -> Silero VAD -> whisper.cpp base (ggml) -> text
-> Path A as-is (default) / Path B API text-fix (optional, OFF)
-> clipboard + result card + history-YYYY-MM.md (forever)

No llama.cpp. No diarization. No Notes app. No cloud audio.

## Borrowed features
* Handy: custom model dir (ggml-*.bin), custom word dictionary, custom tap action,
  single instance, history store.
* OpenWhispr: autostart on boot, foreground service lifecycle, floating overlay
  settings (position, size, color), quick tile.

## Android mapping for global keys
No true PC global hotkey on Android. Provide:
1. Floating bubble tap (main, v0.1)
2. Quick Settings tile (v0.1)
3. Volume-long-press via Accessibility (later)
4. Bluetooth button (later)

## Settings (v0.1)
* mic source, language en/auto, model base (+ custom dir)
* VAD on/off + strict 1-3
* AI fix: off / api (key field, default off)
* bubble: position 4 corners, size S/M, colors, card duration 3/5/10s
* outputs: clipboard, card, history file toggles
* update channel: stable/beta, check now button

## Storage
* models: files/models/ggml-base.bin (+ sha256)
* history: files/history/history-YYYY-MM.md, append `- HH:MM text`
* config: files/config.yaml (single file, see config.yaml)
* no SQL in v0.1 (plain markdown keeps it simple + forever-safe)

## OTA
* ota/latest.json in repo, attached to every GitHub Release as latest.json
* app compares semver, downloads APK to cache, fires install intent
* release workflow builds arm64 debug+release APK
