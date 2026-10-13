# On The Go

Offline voice-to-text for Android. Tap bubble, speak, get text.

* 100% offline by default. No cloud. No account.
* Engine: `whisper.cpp` base (~142MB, ggml), CPU direct, no Termux.
* VAD: Silero (speech only, skip silence).
* AI: none by default. Optional API text-fix only (OFF by default, no local LLM).
* Output: clipboard + result card + `history-YYYY-MM.md` forever (monthly split).
* UI: floating bubble + result card + simple settings. English only.
* System: autostart on boot, foreground service, single instance.
* OTA: checks GitHub Releases `latest.json`, downloads APK, asks install.

## Quick start (user)

1. Install APK from Releases.
2. Grant mic + overlay + battery no-kill.
3. Model downloads once to app storage.
4. Tap bubble to record, tap again to get text.

## Quick start (dev)

Needs Android Studio + NDK r26+. `whisper.cpp` is a submodule, fetched at build.

```bash
git clone --recurse-submodules https://github.com/shubhnayanoffsi/On-The-Go.git
# open in Android Studio, run on arm64-v8a device
```

See `docs/SPEC.md` for locked spec.

## OTA

App reads `https://github.com/shubhnayanoffsi/On-The-Go/releases/latest/download/latest.json`:

```json
{ "version": "0.1.0", "apk": "OnTheGo-v0.1.0.apk", "note": "" }
```

If `version` > installed, download APK from Releases and prompt install.
