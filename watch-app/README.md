# PowerFace – battery-saving watch faces for the M99 / H20 Android watch

An app for the M99 (H20) 4G Android watch: Android 10, 720×720 AMOLED screen.
When you apply a face, everything else is put to sleep and only the face stays on screen, dim and
redrawn once a minute.

## How it works

Open the app → **Apply**, **Customize faces**, **Create new face**, **Power settings**.

- **Apply**: starts power mode with the last selected face.
- **Customize**: all your faces, each with Apply / Edit / Copy / Delete.
- **Create new**: the editor. Add Time, Date, Battery, Temp, Steps, Analog dial or your own text,
  drag items, change size, colour, gradient and font, show Battery / Steps as a ring, and use a gallery
  photo as the background (with a Darker setting to save battery).
- **100 built-in designs**: 10 layouts (Giant, Orbit, Rings, Dashboard, Minimal, Split, Neon Frame, Sport,
  Analog Pro, Grid) × 10 glowing themes (Cyber, Aurora, Sunset, Ice, Lava, Matrix, Royal, Sakura, Ocean, Mono),
  all laid out for the square screen. Gallery with filters and favourites; Customize any design to make your own copy.
- **Editor (v2)**: Add · Layers · Style · Background tabs. Tap an item to select it, drag to move (snaps to centre),
  pinch to resize, Undo. Layers list with ▲ ▼ ✕ to reorder and delete. Style: colour, gradient, glow, opacity,
  9 fonts, italic, outline, CAPS, letter spacing, date formats, ring/bar/arc meters, dial styles, shapes
  (circle, ring, glass box, frame, line, arc). New faces start empty.

### Power mode
- Closes background processes of other apps.
- Optional: turns Wi-Fi and Bluetooth off, and turns them back on when you exit.
- Optional: screen pinning, which locks the watch to the face.
- Low brightness (adjustable), tap to brighten for 5 s.
- Redraws only once a minute; the step sensor batches its updates.
- Moves the face a few pixels each minute to protect the AMOLED from burn-in.
- Swipe left/right to change face (favourites and your own faces).
- Shows what it did when it starts (apps closed, Wi-Fi/Bluetooth off, brightness), and the main screen shows
  how much battery the last power-mode session used per hour.
- **Long-press anywhere to exit.**

Temperature comes from Open-Meteo (free, no key) for your last known location, refreshed at most once an hour.

## Getting the APK

Every push that changes `watch-app/` runs the **Build PowerFace watch app** GitHub Action.
1. Open the repo's **Releases** page on your phone → latest `PowerFace build N` → download the `.apk`.
2. Install it on the phone to test.
3. Send it to the watch with LocalSend and install it there.

Every build uses the same signing key, so a new APK installs over the old one and keeps your faces.

## Notes
- `targetSdk` is 28 on purpose, so the app can switch Wi-Fi and Bluetooth off on Android 10. The app is for
  sideloading only, not for the Play Store.
- `app/debug.keystore` is a throwaway debug key, kept in the repo so updates install in place.
