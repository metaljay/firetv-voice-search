# Stremio Voice for Fire TV

Hold the voice button on a rooted Fire TV Stick's remote, say a title (with or without "play"), and Stremio opens on the search results for it. No Alexa conversation and no Amazon results screen in between.

![The bar while the voice button is held](docs/listening.png)

![The bar after the button is released](docs/searching.png)

> **Unofficial and experimental.** Not affiliated with or endorsed by Stremio or Amazon. It installs a system app through Magisk and has been tested on one device. Use it at your own risk.

## Why

On a stock stick the voice button belongs to Alexa. Say a plain title and you land on Amazon's own results screen. Say "play" and a title, and Alexa asks which service to use. If Stremio is where you watch, neither gets you there.

## What it does

- **Hold the voice button:** a bar appears along the bottom of the screen and Alexa's usual listening sounds play.
- **Let go:** lead-ins and tails such as "play", "watch", "search for" and "on Stremio" are dropped, and Stremio opens on the results for what is left.
- **Say nothing:** nothing happens.
- **If the app stops running:** the button goes back to Alexa within about 10 seconds, and the app is restarted.

## Tested on

| Device | Fire OS | Magisk | Stremio | Language |
|---|---|---|---|---|
| Fire TV Stick 4K Max (2nd gen), AFTKRT / karat | 8.1.8.5 | 31.1 | 1.12.0 (`com.stremio.one`) | English (UK) |

Nothing else has been tested. Reports from other devices and Fire OS versions are welcome.

## Before you install

- **It installs a privileged system app.** On firmware that differs from the tested one it may do nothing, or the stick may fail to finish booting. Know your way back first: Magisk switches every module off after two failed boots in a row (switch your other modules back on afterwards); over adb as root, create `/data/adb/modules/stremio_voice/remove` and restart; or remove the module folder from recovery.
- **It takes over the voice button.** While it runs, Alexa voice commands from the remote do not work.
- **It relies on undocumented Fire OS behaviour.** A system update can break it.
- **Any app on the stick can start a listening session,** because the button broadcast it listens for is not protected. The words it hears are written to the system log.

## Install

You need a rooted Fire TV with Magisk, and Stremio for Android TV.

Use the module zip (`stremio_voice.zip`) from a release, or build it yourself as described below. GitHub's automatic source zip is not a module.

In the Magisk app: Modules, Install from storage, pick the zip, restart. Or over adb:

```bash
ADB="adb -s <stick-address>:5555"
$ADB push stremio_voice.zip /data/local/tmp/
$ADB shell 'su -c "magisk --install-module /data/local/tmp/stremio_voice.zip"'
$ADB reboot
```

Give it about a minute after the restart, then hold the voice button.

## Remove

- **Switch off:** create the file `/data/adb/modules/stremio_voice/disable` as root, then restart. Alexa has the button again and the app is gone.
- **Switch back on:** delete that file and restart.
- **Uninstall for good:** create `/data/adb/modules/stremio_voice/remove` and restart, or remove it in the Magisk app.

## How it works

1. Fire OS has a built-in switch, the system property `debug.alm.enable`. When it is `true`, a press of the voice button (key code 322) is sent out as a broadcast, `com.amazon.tv.alm.action.KEY_INFO`, once on button-down and once on button-up, instead of starting Alexa.
2. A small system app (`com.metaljay.stremiovoice`) listens for that broadcast. On button-down it starts the speech-to-text service that ships inside Alexa's own app, a standard Android `RecognitionService`, and shows the bar. On button-up it stops listening.
3. The words that come back are tidied (`QueryCleaner`: "Alexa, play Peppa Pig please" becomes `peppa pig`) and opened with `stremio:///search?search=<words>`.
4. The Magisk module installs the app in `priv-app`, because the speech service needs a `signature|privileged` permission, and runs a small watchdog every 10 seconds. While the app is alive it keeps the switch on. If the app dies, the switch goes off, Alexa gets the button back, and the app is restarted.

There are no hooks, no Zygisk, and no patched or replaced Amazon files. Recognition uses whichever language Alexa is set to on the stick.

## Build

The build script was written on macOS and downloads nothing. It needs a JDK (25 by default; set `JAVA_HOME` to use another), Android build-tools 36.0.0 and `platforms/android-34` (set `ANDROID_HOME` if the SDK is not in `~/Library/Android/sdk`).

```bash
./build.sh test   # unit tests for the word-tidying rules
./build.sh        # tests, then build/StremioVoice.apk and build/stremio_voice.zip
```

The first build creates `debug.keystore` (git-ignored). Keep it: updates installed with `adb install -r` must be signed with the same key.

For quick changes to the app alone, with no restart once the module is in:

```bash
$ADB install -r build/StremioVoice.apk
```

## Checking it is alive

```bash
$ADB shell getprop debug.alm.enable        # true while the app is running
$ADB logcat -s StremioVoice                # what was heard and what was searched
```

## Other apps

It opens Stremio because that is the only app I use on my own stick. Pull requests that add other apps, such as Nuvio or Kodi, are welcome. The target lives in one method, `openStremio()` in `VoiceService.java`; an app needs a search link that can be opened with the spoken words.

## Reporting problems

Please include the device, the full Fire OS version, the Magisk version, the module version, and the output of `logcat -s StremioVoice` from a failed attempt.

## Layout

| Path | What |
|---|---|
| `build.sh` | test and build script |
| `app/` | the Android app (plain Java, no libraries) |
| `test/` | plain-Java tests for `QueryCleaner` |
| `module/` | the Magisk module: `module.prop`, `service.sh`, the permission whitelist |
| `docs/` | screenshots |

## Licence

MIT, see [LICENSE](LICENSE). Stremio, Alexa and Fire TV are trademarks of their owners.
