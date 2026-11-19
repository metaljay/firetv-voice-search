# Voice Search for Fire TV (Stremio, Nuvio and WuPlay)

Hold the voice button on a rooted Fire TV Stick's remote, say a title (with or without "play"), and your streaming app opens on the results for it. No Alexa conversation and no Amazon results screen in between. Works with **Stremio**, **Nuvio** and **WuPlay**; you choose which in the **Voice Search** app.

*(Formerly "Stremio Voice". Existing installs update in place.)*

![The bar while the voice button is held](docs/listening.png)

![The bar after the button is released](docs/searching.png)

> **Unofficial and experimental.** Not affiliated with or endorsed by Stremio, Nuvio, WuPlay or Amazon. It installs a system app through Magisk and has been tested on one device. Use it at your own risk.

## Why

On a stock stick the voice button belongs to Alexa. Say a plain title and you land on Amazon's own results screen. Say "play" and a title, and Alexa asks which service to use. If Stremio, Nuvio or WuPlay is where you watch, none of that gets you there.

## What it does

- **Hold the voice button:** a bar appears along the bottom of the screen and Alexa's usual listening sounds play.
- **Let go:** lead-ins and tails such as "play", "watch", "search for" and "on Stremio" are dropped, and the words left are searched in your chosen app:
  - **Stremio** opens on its own search results.
  - **Nuvio** has no way for another app to start a search, so a row of matching posters appears instead (films and TV together, best matches first). Pick one with the remote and Nuvio opens on it. Back closes the row.
  - **WuPlay** works the same way as Nuvio: pick from the row of posters and WuPlay opens on it. With more than one WuPlay profile, WuPlay may ask who's watching first.
- **Choosing the app:** open the **Voice Search** tile in Your Apps & Channels, or hold the voice button and say **"Voice settings"** (or "Voice Search settings"). Pick Stremio, Nuvio or WuPlay; the screen confirms with "Saved", and **Done** closes it. Apps that aren't installed are greyed out. If you never choose, it uses whichever supported app is installed (Stremio first, then Nuvio, then WuPlay).
- **Say nothing:** nothing happens.
- **If the app stops running:** the button goes back to Alexa within about 10 seconds, and the app is restarted.

## Tested on

| Device | Fire OS | Magisk | Stremio | Nuvio | WuPlay | Language |
|---|---|---|---|---|---|---|
| Fire TV Stick 4K Max (2nd gen), AFTKRT / karat | 8.1.8.5 | 31.1 | 1.12.0 (`com.stremio.one`) | 1.1.0 (`com.nuvio.tv`, 32-bit build) | 0.9.5-beta (`app.wuplay.androidtv`, 32-bit build) | English (UK) |

Nothing else has been tested. Reports from other devices and Fire OS versions are welcome.

## Before you install

- **It installs a privileged system app.** On firmware that differs from the tested one it may do nothing, or the stick may fail to finish booting. Know your way back first: Magisk switches every module off after two failed boots in a row (switch your other modules back on afterwards); over adb as root, create `/data/adb/modules/stremio_voice/remove` and restart; or remove the module folder from recovery.
- **It takes over the voice button.** While it runs, Alexa voice commands from the remote do not work.
- **It relies on undocumented Fire OS behaviour.** A system update can break it.
- **Any app on the stick can start a listening session,** because the button broadcast it listens for is not protected. The words it hears are written to the system log.
- **With Nuvio or WuPlay, the spoken words are sent to Cinemeta** (`v3-cinemeta.strem.io`, the free catalogue Stremio and Nuvio themselves use) to find matching titles.

## Install

You need a rooted Fire TV with Magisk, and Stremio, Nuvio or WuPlay for Android TV. The Fire TV Stick 4K Max runs 32-bit apps only, so use Nuvio's `armeabi-v7a` build there (WuPlay has one download that holds both 32- and 64-bit code, and the stick picks the 32-bit part itself).

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

- **Switch off:** create the file `/data/adb/modules/stremio_voice/disable` as root, then restart. Alexa has the button again and the app is gone. The Voice Search tile stays and only shows a message that the add-on is off.
- **Switch back on:** delete that file and restart.
- **Uninstall for good:** create `/data/adb/modules/stremio_voice/remove` and restart, or remove it in the Magisk app. Then uninstall the **Voice Search** tile app (`com.metaljay.voicesearchtile`) in Settings, Applications; on its own it only shows a message that the add-on is gone. (While the add-on is installed, an uninstalled tile comes back at the next restart.)

## How it works

1. Fire OS has a built-in switch, the system property `debug.alm.enable`. When it is `true`, a press of the voice button (key code 322) is sent out as a broadcast, `com.amazon.tv.alm.action.KEY_INFO`, once on button-down and once on button-up, instead of starting Alexa.
2. A small system app (`com.metaljay.stremiovoice`) listens for that broadcast. On button-down it starts the speech-to-text service that ships inside Alexa's own app, a standard Android `RecognitionService`, and shows the bar. On button-up it stops listening.
3. The words that come back are tidied (`QueryCleaner`: "Alexa, play Peppa Pig please" becomes `peppa pig`). For Stremio they are opened with `stremio:///search?search=<words>`. For Nuvio, which only accepts links to one title, the pick-list (`ResultsActivity`) looks the words up in Cinemeta's film and series catalogues, orders them (`ResultRanker`: popularity first, with a small penalty for weaker name matches) and opens the chosen one with `nuvio://movie/<imdb id>` or `nuvio://series/<imdb id>` (WuPlay: `wuplay://movie/<imdb id>` or `wuplay://series/<imdb id>`).
4. The Magisk module installs the app in `priv-app`, because the speech service needs a `signature|privileged` permission, and runs a small watchdog every 10 seconds. While the app is alive it keeps the switch on. If the app dies, the switch goes off, Alexa gets the button back, and the app is restarted.
5. Amazon's home screen hides system apps, so the settings screen can't have a tile of its own. The module carries a second, ordinary app (`com.metaljay.voicesearchtile`, in `tile/`) whose only job is to show a **Voice Search** tile and open the settings screen. The system app has no home-screen entry of its own, so home screens that do show system apps list Voice Search once. `service.sh` installs the tile app at boot when it is missing or has changed.
6. Early in each boot, `post-fs-data.sh` clears Android's cached details for this app when the app file has changed. Without it, an updated module kept the old version's details: Android trusts its cache while the cache is newer than the app's folder, and through Magisk that folder shows the boot-time clock, which on Fire TV reads 1 Jan 2022.

There are no hooks, no Zygisk, and no patched or replaced Amazon files. Recognition uses whichever language Alexa is set to on the stick.

## Build

The build script was written on macOS and downloads nothing. It needs a JDK (25 by default; set `JAVA_HOME` to use another), Android build-tools 36.0.0 and `platforms/android-34` (set `ANDROID_HOME` if the SDK is not in `~/Library/Android/sdk`).

```bash
./build.sh test   # unit tests: word tidying, choosing the app, ordering results
./build.sh        # tests, then build/StremioVoice.apk, build/VoiceSearchTile.apk and build/stremio_voice.zip
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

## Adding another app

The supported apps are a list in `Targets.java`. An app can be added in one of two ways:

- **It has a search link** that takes words (like Stremio): add an entry with mode `APP_SEARCH` and the link.
- **It can only open one title by IMDb id** (like Nuvio): add an entry with mode `PICK_LIST`, and its title-link format in `Targets.titleLink()`.

Pull requests are welcome; please add a case to `test/TargetsTest.java`.

## Reporting problems

Please include the device, the full Fire OS version, the Magisk version, the module version, and the output of `logcat -s StremioVoice` from a failed attempt.

## Layout

| Path | What |
|---|---|
| `build.sh` | test and build script |
| `app/` | the Android app (plain Java, no libraries) |
| `tile/` | the small tile app that opens the settings screen |
| `test/` | plain-Java tests for `QueryCleaner`, `Targets` and `ResultRanker` |
| `module/` | the Magisk module: `module.prop`, `post-fs-data.sh`, `service.sh`, the permission whitelist |
| `docs/` | screenshots |

## Licence

MIT, see [LICENSE](LICENSE). The microphone icon shape is from Google's Material Icons (Apache 2.0). Stremio, Nuvio, WuPlay, Alexa and Fire TV are trademarks of their owners.
