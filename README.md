# Voice Search for Fire TV (Stremio, Nuvio, WuPlay, TiviMate, SmartTube and Amazon search)

Hold the voice button on a rooted Fire TV Stick's remote, say a title (or, in TiviMate, a channel; with or without "play"), and your streaming app opens on the results for it. No Alexa conversation and no Amazon results screen in between (unless you ask for Amazon's). Works with **Stremio**, **Nuvio**, **WuPlay**, **TiviMate** (channels, films and series; experimental) and **SmartTube**; you choose the usual one in the **Voice Search** app, and you can say **"on <app>"** to pick another for one search: "Bluey on SmartTube". Apps that have no search of their own (Netflix, Disney+, BBC iPlayer, Channel 4, My5, Prime Video) are sent to **Amazon's own results screen**, which lists matches from all your apps. Saying just **"Alexa"** hands the next press of the button to real Alexa.

*(Formerly "Stremio Voice". Existing installs update in place.)*

<a href="https://buymeacoffee.com/metaljay"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me a Coffee" height="40"></a>

![The bar while the voice button is held](docs/listening.png)

![The bar after the button is released](docs/searching.png)

> **Unofficial and experimental.** Not affiliated with or endorsed by Stremio, Nuvio, WuPlay, TiviMate or Amazon. It installs a system app through Magisk and has been tested on one device. Use it at your own risk.

## Why

On a stock stick the voice button belongs to Alexa. Say a plain title and you land on Amazon's own results screen. Say "play" and a title, and Alexa asks which service to use. If Stremio, Nuvio, WuPlay or TiviMate is where you watch, none of that gets you there.

## What it does

- **Hold the voice button:** a bar appears along the bottom of the screen and Alexa's usual listening sounds play.
- **Let go:** lead-ins and tails such as "play", "watch", "search for" and "on Stremio" are dropped, and the words left are searched in your chosen app, or in the app you named at the end ("Bluey on Stremio", "Peppa Pig on SmartTube", "Frozen in Nuvio"). The chosen app:
  - **Stremio** opens on its own search results.
  - **Nuvio** has no way for another app to start a search, so a row of matching posters appears instead (films and TV together, best matches first). Pick one with the remote and Nuvio opens on it. Back closes the row.
  - **WuPlay** works the same way as Nuvio: pick from the row of posters and WuPlay opens on it. With more than one WuPlay profile, WuPlay may ask who's watching first.
  - **SmartTube** opens on its own search results (`https://www.youtube.com/results?search_query=<words>`).
  - **Amazon search** opens Amazon's own results screen, which lists matches from all your apps (including Netflix, Disney+ and the rest). It needs one extra permission, see below.
  - **TiviMate** (experimental) has no search link, so Voice Search opens TiviMate, goes to TiviMate's own **Search** screen, types your words in, and highlights the best result (films and series first, the title that best matches your words; say "... channel", for example "Bluey channel", to get the live channel instead); press OK to play it. One search covers live channels, films and series (TiviMate shows them in Channels, Movies and Shows rows). It works from any screen, in about 2 to 5 seconds, and you see TiviMate's menu flick past on the way. A spoken "channel" at the end is dropped ("sky news channel" searches "sky news"), because TiviMate's search wouldn't find the extra word. If TiviMate's screens change in an update, it may stop short of the Search screen; nothing breaks, you just land in TiviMate.
- **Choosing the app:** open the **Voice Search** tile in Your Apps & Channels, or hold the voice button and say **"Voice settings"** (or "Voice Search settings"). Pick Stremio, Nuvio, WuPlay, TiviMate, SmartTube or Amazon search; the screen confirms with "Saved", and **Done** closes it. Apps that aren't installed are greyed out. If you never choose, it uses whichever supported app is installed (Stremio first, then Nuvio, WuPlay, TiviMate, SmartTube; Amazon search is only used when you choose it).
- **Saying the app's name:** the top row of the settings screen, **Say the app's name**, is on by default. On: "Bluey on SmartTube" searches SmartTube whatever app you chose. Off: the name is still ignored, and the app you chose is always used. If you name an app that isn't installed, the bar says so ("Stremio isn't installed, searching in Nuvio") and your chosen app is used. If you name an app with no search ("Bluey on Netflix", Disney+, BBC iPlayer, Channel 4 / All 4, My5, Prime Video, Amazon), Amazon's results screen opens for it, and the bar says "Netflix has no search, showing Amazon's results" (if that can't be opened, the app itself is opened instead). Speech-to-text spellings such as "smart tube", "tivi mate" and "woo play" are understood.
- **Saying "Alexa":** a bare "Alexa" (or "Hey Alexa") gives the **next** press of the button to real Alexa: the bar says "Hold the button again and ask Alexa". For 15 seconds the button is Alexa's, then it is Voice Search's again. (Alexa's own text input can't be reached from another app, so it has to be the next press.) Anything else you say after "Alexa" ("Alexa play Bluey") is just searched, as before.
- **Say nothing:** nothing happens.
- **If the app stops running:** the button goes back to Alexa within about 10 seconds, and the app is restarted.
- **YouTube (Amazon's app):** you can say "on YouTube", but Amazon's YouTube opens on "who's watching" first, so the search may not survive that step; it is not in the settings list.

## Tested on

| Device | Fire OS | Magisk | Stremio | Nuvio | WuPlay | TiviMate | Language |
|---|---|---|---|---|---|---|---|
| Fire TV Stick 4K Max (2nd gen), AFTKRT / karat | 8.1.8.5 | 31.1 | 1.12.0 (`com.stremio.one`) | 1.1.0 (`com.nuvio.tv`, 32-bit build) | 0.9.5-beta (`app.wuplay.androidtv`, 32-bit build) | 5.3.3 (`ar.tvplayer.tv`) | English (UK) |

Nothing else has been tested. Reports from other devices and Fire OS versions are welcome.

## Before you install

- **It installs a privileged system app.** On firmware that differs from the tested one it may do nothing, or the stick may fail to finish booting. Know your way back first: Magisk switches every module off after two failed boots in a row (switch your other modules back on afterwards); over adb as root, create `/data/adb/modules/stremio_voice/remove` and restart; or remove the module folder from recovery.
- **It takes over the voice button.** While it runs, Alexa voice commands from the remote do not work, except for 15 seconds after you say "Alexa" on its own.
- **For Amazon search it asks for one more system permission,** `com.amazon.tv.launcher.permission.SHOW_SEARCH_RESULTS`, which lets it open Amazon's own search results screen. It is a platform permission that only system apps on the add-on's whitelist (`privapp-permissions-com.metaljay.stremiovoice.xml`) can hold. Both the app's manifest and that whitelist must list it, because on this firmware a system app asking for a privileged permission that is not whitelisted stops the stick booting; `build.sh` fails the build if the two ever disagree.
- **It relies on undocumented Fire OS behaviour.** A system update can break it.
- **Any app on the stick can start a listening session,** because the button broadcast it listens for is not protected. The words it hears are written to the system log.
- **For TiviMate it turns on a small accessibility helper.** Typing into another app's screen is what Android's accessibility feature is for, and there is no other way into TiviMate. The helper (`TiviMateHelper`) gets events from TiviMate only (Android filters them), and it reads nothing on screen unless TiviMate is in front. It asks for no extra permission. The add-on switches it on only while TiviMate is the app Voice Search is using, which is the one you chose, or the first installed one if you haven't chosen (it adds one entry to Android's `enabled_accessibility_services` and, if that was the only one, puts `accessibility_enabled` back to what it was) and switches it off when that changes to another app. It acts only in the 10 seconds after you speak, and does nothing the rest of the time. (If you say "on TiviMate" while another app is chosen, the helper is switched on for a minute, then off again.) Fire OS may show it in Settings, Accessibility.
- **With Nuvio or WuPlay, the spoken words are sent to Cinemeta** (`v3-cinemeta.strem.io`, the free catalogue Stremio and Nuvio themselves use) to find matching titles.
- **It checks GitHub for updates** (`api.github.com`, nothing else, nothing sent about you). Switch that off any time with **Off** on the Updates screen. See [Updates](#updates).

## Install

You need a rooted Fire TV with Magisk, and at least one of Stremio, Nuvio, WuPlay, TiviMate or SmartTube for Android TV (Amazon search needs none). The Fire TV Stick 4K Max runs 32-bit apps only, so use Nuvio's `armeabi-v7a` build there (WuPlay has one download that holds both 32- and 64-bit code, and the stick picks the 32-bit part itself).

Use the module zip (`voice_search.zip`) from a release, or build it yourself as described below. GitHub's automatic source zip is not a module.

In the Magisk app: Modules, Install from storage, pick the zip, restart. Or over adb:

```bash
ADB="adb -s <stick-address>:5555"
$ADB push voice_search.zip /data/local/tmp/
$ADB shell 'su -c "magisk --install-module /data/local/tmp/voice_search.zip"'
$ADB reboot
```

Give it about a minute after the restart, then hold the voice button.

## Updates

From version 2.3 Voice Search can update itself. **Install 2.3 by hand once, as above; after that, updates come from inside the app.** (If you are on 2.2 or older, install 2.3 by hand one last time.)

Open the settings screen (the **Voice Search** tile, or say "Voice settings") and choose **Updates**:

- **How often it checks:** Daily, Weekly (the default), Monthly or Off. **Check now** always works. With no connection, scheduled checks stay quiet and try again later; Check now says it couldn't check.
- **How you hear about one:** Fire OS shows no pop-up for an app's notification (they only collect in its Notification Center), so a short banner appears along the bottom of the screen ("Voice Search 2.3.1 is ready…"), a card is added to the Notification Center, and the settings screen shows "Update available". Nothing is installed until you choose.
- **The offer:** a plain summary of what changed, then **Install**, **Not now** or **Skip this version** (a skipped version is never announced again, though Check now still shows it). A release whose notes start with "Urgent:" is called an urgent fix. Only full GitHub releases are offered, never pre-releases.
- **After installing:** choose **Restart now** or **Later**. The update isn't finished until the stick restarts, and the screens say so.
- **Go back:** the previous version is kept on the stick. **Go back to version X** restores it in one tap, offline, one step back, then asks for a restart. Your settings (which app to search in, how often to check) are kept both ways.
- **If a new version doesn't start,** the previous one is put back automatically and a notification says so. The stick never restarts by itself; restart when it suits you.

How it stays safe:

- The release zip is signed (as a jar) with the same key that signs the app. The app refuses a download unless every file in it is signed, nothing has been changed, nothing is missing or added, and the signer is exactly its own key. A failed or interrupted download installs nothing and leaves the working version alone.
- The app has no root and gets no new permission. It hands the checked zip to the add-on's `service.sh`, which runs as root: it checks the zip's SHA-256 again, keeps a copy of the current version, unpacks it with Magisk's own tools into a temporary folder, checks it there, and moves it into place in one step for Magisk to apply at the next restart (so a failed install never leaves a half-installed module), and, when you tap Restart now, restarts. Installing by hand through Magisk still works alongside.

Maintainers: the first paragraph of a release's notes is the summary the TV shows (about 300 characters), so write it for the person holding the remote. Attach `build/voice_search.zip` **and** `build/stremio_voice.zip` (the same file under the old name) to every release, exactly as `build.sh` produces them; they are already signed. Keep attaching both: Voice Search 2.3 and earlier only look for the old name, and the app takes the new name first.

## Remove

- **Switch off:** create the file `/data/adb/modules/stremio_voice/disable` as root, then restart. Alexa has the button again and the app is gone. The Voice Search tile stays and only shows a message that the add-on is off.
- **Switch back on:** delete that file and restart.
- **If TiviMate was the chosen app when you switched off this way,** the helper's entry stays in Android's accessibility list, and `accessibility_enabled` stays at 1 (the add-on can't clean up while it is off; the entry then points at an app that is gone, and does nothing). Switching the add-on back on and choosing another app removes it, and so does uninstalling (below).
- **Uninstall for good:** create `/data/adb/modules/stremio_voice/remove` and restart, or remove it in the Magisk app. The helper's entry is taken out of the accessibility list for you (by `uninstall.sh`, shortly after that restart finishes booting). Then uninstall the **Voice Search** tile app (`com.metaljay.voicesearchtile`) in Settings, Applications; on its own it only shows a message that the add-on is gone. (While the add-on is installed, an uninstalled tile comes back at the next restart.)

## How it works

1. Fire OS has a built-in switch, the system property `debug.alm.enable`. When it is `true`, a press of the voice button (key code 322) is sent out as a broadcast, `com.amazon.tv.alm.action.KEY_INFO`, once on button-down and once on button-up, instead of starting Alexa.
2. A small system app (`com.metaljay.stremiovoice`) listens for that broadcast. On button-down it starts the speech-to-text service that ships inside Alexa's own app, a standard Android `RecognitionService`, and shows the bar. On button-up it stops listening.
3. The words that come back are tidied (`QueryCleaner`: "Alexa, play Peppa Pig please" becomes `peppa pig`). For Stremio they are opened with `stremio:///search?search=<words>`. For Nuvio, which only accepts links to one title, the pick-list (`ResultsActivity`) looks the words up in Cinemeta's film and series catalogues, orders them (`ResultRanker`: popularity first, with a small penalty for weaker name matches) and opens the chosen one with `nuvio://movie/<imdb id>` or `nuvio://series/<imdb id>` (WuPlay: `wuplay://movie/<imdb id>` or `wuplay://series/<imdb id>`).
4. The Magisk module installs the app in `priv-app`, because the speech service needs a `signature|privileged` permission, and runs a small watchdog every 10 seconds. While the app is alive it keeps the switch on. If the app dies, the switch goes off, Alexa gets the button back, and the app is restarted. The same script looks once a second for an `alexa` file the app leaves in its shared folder (when you say just "Alexa"): it switches the diversion off for 15 seconds, so the next press starts real Alexa, then switches it back on. The switch is read live, which is also why the watchdog's hand-back works at once.
   Which app gets a search: `Targets.named()` finds a trailing "on/in <app>" in what was said; `VoiceService` uses that app if the "Say the app's name" switch is on and the app is installed, otherwise your chosen one. Amazon's results screen (`com.amazon.tv.launcher/.deeplink.activity.SearchResultsDeepLinkActivityDI`) is started with the extras `term`, `text` and `source`.
5. TiviMate has no search link, so it is driven like a person with a remote, through the accessibility helper (`TiviMateHelper`; `res/xml/tivimate_helper.xml` makes Android send it events from TiviMate only, and the code reads the screen only while TiviMate is in front). The service puts the words in `PendingSearch` and opens TiviMate; the helper then, on each look at TiviMate's screen, does the first thing that fits: fills in the search box (`ACTION_SET_TEXT`, which never raises Amazon's on-screen keyboard), waits until TiviMate's results have stopped changing (about 0.7 s of quiet, at most 3 s, because it adds its rows one at a time), then focuses the best card: `ResultChooser` prefers films and series over channels and picks the title that best matches your words (among equal matches, a plain "Sky News" before a prefixed one, then the stick's own country's "UK: Sky News" before other countries'), and saying "channel" at the end ("bluey channel") prefers the channel instead; or clicks the menu's Search item (only when the menu has the remote's focus); or presses Back, at most four times, to reach the menu. It gives up quietly after 10 seconds. `service.sh` adds or removes the helper's entry in the accessibility list (`a11y_list.sh`, tested with plain `sh`) whenever the chosen app changes, and removes it before staging "Go back" or an automatic revert, since older versions know nothing about it.
6. Amazon's home screen hides system apps, so the settings screen can't have a tile of its own. The module carries a second, ordinary app (`com.metaljay.voicesearchtile`, in `tile/`) whose only job is to show a **Voice Search** tile and open the settings screen. The system app has no home-screen entry of its own, so home screens that do show system apps list Voice Search once. `service.sh` installs the tile app at boot when it is missing or has changed.
7. Early in each boot, `post-fs-data.sh` clears Android's cached details for this app when the app file has changed. Without it, an updated module kept the old version's details: Android trusts its cache while the cache is newer than the app's folder, and through Magisk that folder shows the boot-time clock, which on Fire TV reads 1 Jan 2022.

8. Updates (`UpdatesActivity`, `Updater`, `UpdateJob`): a scheduled job asks GitHub for the latest release about every six hours, and `UpdateSchedule` decides whether a check is due. `PackageVerifier` proves a download genuine before the app asks `service.sh` to install it; the two talk through a `request` and a `status` file in the app's private folder. After an update `service.sh` watches the new version's first two minutes and puts the old one back if the app never runs.

There are no hooks, no Zygisk, and no patched or replaced Amazon files. Recognition uses whichever language Alexa is set to on the stick.

## Build

The build script was written on macOS and downloads nothing. It needs a JDK (25 by default; set `JAVA_HOME` to use another), Android build-tools 36.0.0 and `platforms/android-34` (set `ANDROID_HOME` if the SDK is not in `~/Library/Android/sdk`).

```bash
./build.sh test   # unit tests: word tidying, choosing and naming the app, "Alexa" detection, ordering results, versions, update schedule, release notes, update-zip checks, choosing the download, the accessibility-list edits, the boot-guard check
./build.sh        # tests, then build/StremioVoice.apk, build/VoiceSearchTile.apk and build/voice_search.zip (signed; also copied to build/stremio_voice.zip, the old name)
```

The first build creates `debug.keystore` (git-ignored). Keep it: updates installed with `adb install -r` must be signed with the same key, and the self-updater only accepts a zip signed with it. If you build your own copy, your key signs your zips, so installs of your build update from your build only; set the repository address in `Updater.java` to your own GitHub project.

For quick changes to the app alone, with no restart once the module is in:

```bash
$ADB install -r build/StremioVoice.apk
```

## Checking it is alive

```bash
$ADB shell getprop debug.alm.enable        # true while the app is running
$ADB logcat -s VoiceSearch                      # what was heard and what was searched (the add-on's own lines: -s VoiceSearchModule)
```

## Adding another app

The supported apps are a list in `Targets.java`. An app can be added in one of three ways:

- **It has a search link** that takes words (like Stremio): add an entry with mode `APP_SEARCH` and the link.
- **It can only open one title by IMDb id** (like Nuvio): add an entry with mode `PICK_LIST`, and its title-link format in `Targets.titleLink()`.
- **It has no search at all** (like Netflix): add it to `NO_SEARCH` with its package; naming it sends the words to Amazon's results screen, or opens the app.
- **It has a search screen of its own but no link** (like TiviMate): mode `TYPE_IN_APP`. The words are typed into that screen by an accessibility helper like `TiviMateHelper`, which has to know the app's screens.

Give each entry the `spokenNames` people may say, lower case as speech-to-text writes them ("smart tube"). Pull requests are welcome; please add a case to `test/TargetsTest.java`.

## Reporting problems

Please include the device, the full Fire OS version, the Magisk version, the module version, and the output of `logcat -s VoiceSearch VoiceSearchModule` from a failed attempt.

## Layout

| Path | What |
|---|---|
| `build.sh` | test and build script (also runs the boot guard, `check_whitelist.sh`) |
| `app/` | the Android app (plain Java, no libraries) |
| `tile/` | the small tile app that opens the settings screen |
| `test/` | plain-Java tests for `QueryCleaner`, `Targets`, `ResultRanker`, `ResultChooser`, `Versions`, `UpdateSchedule`, `ReleaseNotes`, `PackageVerifier` and `AssetPicker`, plus plain-`sh` tests of the accessibility-list edits and the boot guard |
| `module/` | the Magisk module: `module.prop`, `post-fs-data.sh`, `service.sh`, `a11y_list.sh`, `uninstall.sh`, the permission whitelist |
| `docs/` | screenshots |

## ☕ Support

This is made and looked after in spare time. If it's useful to you, you can [buy me a coffee](https://buymeacoffee.com/metaljay). Thank you!

<a href="https://buymeacoffee.com/metaljay"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me a Coffee" height="40"></a>

## Licence

MIT, see [LICENSE](LICENSE). The microphone icon shape is from Google's Material Icons (Apache 2.0). Stremio, Nuvio, WuPlay, TiviMate, SmartTube, YouTube, Netflix, Alexa and Fire TV are trademarks of their owners.
