# MIDI Surface

A custom MIDI control surface for Android, built to drive Gig Performer 5 over the
phone's USB-MIDI endpoint. Free-form layout: faders, knobs and buttons can be mixed on
one page and dragged/resized anywhere.

## What's inside

- `app/src/main/assets/index.html` - the whole UI (HTML/CSS/JS). No framework.
- `app/src/main/java/.../MainActivity.java` - WebView shell.
- `app/src/main/java/.../MidiBridge.java` - Android MIDI API bridge (`AndroidMidi.*`).
- `.github/workflows/build.yml` - builds a debug APK in the cloud, no local SDK needed.

Default mapping:

| Control | CC | Channel |
|---|---|---|
| Fader 1-4 | 7 | 1-4 |
| Buttons 1-4 | 20-23 | 1-4 |

Edit any control in the app: tap **Edit**, tap the control, change type/label/channel/CC/colour.

## Preview the UI without building

Open `app/src/main/assets/index.html` in desktop Chrome. It runs with a stub bridge,
so the layout, editing, drag and resize all work; CCs are printed to the console.

## Build the APK with GitHub (no local install)

1. Create a free account at github.com and a new **private** repository.
2. On the repo page: **Add file -> Upload files**, then drag the *contents* of this
   folder (so `settings.gradle`, `app/`, `.github/` land at the repo root). Commit.
3. If the `.github` folder did not upload (browsers sometimes skip dot-folders):
   **Add file -> Create new file**, name it `.github/workflows/build.yml`, paste the
   contents of that file, commit.
4. Open the **Actions** tab, allow workflows if asked, and run **Build APK**
   (it also runs automatically on push).
5. When it finishes (~3-5 min), open the run and download the `midi-surface-debug`
   artifact (a ZIP). Extract `app-debug.apk`.

## Install

1. Copy `app-debug.apk` to the phone (USB file transfer / Drive / email).
2. Tap it and allow "Install unknown apps" for the app you opened it with.
3. Set the phone's USB mode to **MIDI**, open the app, tap **MIDI: off**,
   and pick the device.

## Map it in Gig Performer 5

1. In GP5, create a widget per channel (fader + mute).
2. Right-click the widget -> MIDI Learn, then move the matching control on the phone.
3. Use **Rig Manager** to alias the device so the mapping survives replugs.
4. Map widgets to Montage's channel volume/mute, or insert a mixer block after
   Montage if the plugin does not expose those parameters.

## Notes

- Debug APK is signed with the standard debug key, fine for personal sideloading.
- If the device list is empty, check Developer options -> Default USB configuration -> MIDI.
- Values are not persisted across restarts; buttons start off and faders at 100.
