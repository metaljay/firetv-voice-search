# Stremio Voice

Hold the voice button on a rooted Fire TV Stick's remote, say a title (with or without "play"), and Stremio's search results for it open. No Amazon screen in between.

## How it works

1. Fire OS has a built-in switch, the system property `debug.alm.enable`. When it is `true`, a press of the voice button is sent out as a broadcast (`com.amazon.tv.alm.action.KEY_INFO`) instead of starting Alexa.
2. A small system app (`com.metaljay.stremiovoice`) listens for that broadcast. On button-down it starts Alexa's own speech-to-text service and shows a slim "Listening…" bar. On button-up it stops listening and shows "Searching…".
3. The words that come back are tidied (`QueryCleaner`: "Alexa, play Peppa Pig please" becomes `peppa pig`) and opened with `stremio:///search?search=<words>`.
4. A Magisk add-on installs the app as a system app, whitelists its one Amazon permission, and runs a small watchdog every 10 seconds. While the app is alive it keeps the switch on. If the app dies, the switch goes off (Alexa gets the button back) and the app is restarted.

## Build

Needs only tools already on the Mac (a JDK, Android build-tools, `platforms/android-34`). Nothing is downloaded.

```bash
./build.sh test   # unit tests for the word-tidying rules
./build.sh        # tests, then build/StremioVoice.apk and build/stremio_voice.zip
```

The first build creates `debug.keystore` (git-ignored). Keep it: updates installed with `adb install -r` must be signed with the same key.

## Install

```bash
ADB="$HOME/Library/Android/sdk/platform-tools/adb -s <stick-address>:5555"
$ADB push build/stremio_voice.zip /data/local/tmp/
$ADB shell 'su -c "magisk --install-module /data/local/tmp/stremio_voice.zip"'
# then restart the stick
```

For quick changes to the app alone (no restart needed once the add-on is in):

```bash
$ADB install -r build/StremioVoice.apk
```

## Remove

- Switch off: create the file `/data/adb/modules/stremio_voice/disable` as root, then restart. Alexa has the button again and the app is gone.
- Switch back on: delete that file and restart.
- Uninstall for good: create `/data/adb/modules/stremio_voice/remove` and restart.

## Checking it is alive

```bash
$ADB shell getprop debug.alm.enable        # true while the app is running
$ADB logcat -s StremioVoice                # what was heard and what was searched
```

## Layout

| Path | What |
|---|---|
| `build.sh` | test and build script |
| `app/` | the Android app (plain Java, no libraries) |
| `test/` | plain-Java tests for `QueryCleaner` |
| `module/` | the Magisk add-on: `module.prop`, `service.sh`, the permission whitelist |
