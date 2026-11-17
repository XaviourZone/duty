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
- 5 built-in faces: Neon Glass, Minimal, Analog Classic, Sport, Info Stack. You can add as many as you like.

### Power mode
- Closes background processes of other apps.
- Optional: turns Wi-Fi and Bluetooth off, and turns them back on when you exit.
- Optional: screen pinning, which locks the watch to the face.
- Low brightness (adjustable), tap to brighten for 5 s.
- Redraws only once a minute; the step sensor batches its updates.
- Moves the face a few pixels each minute to protect the AMOLED from burn-in.
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
