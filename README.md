# SiteLedger

SiteLedger is an offline-first Android app for managing construction-site labour, attendance, wages, and site-owner or supplier accounts. It keeps records on the device, so you can use it without an internet connection.

## Features

- **Labour:** add, edit, archive, and restore workers; keep daily and half-day wage rates.
- **Attendance:** view the month in a spreadsheet-style grid. Worker names stay fixed while the days scroll horizontally. Tap a cell to mark or edit a day as present, half-day, or absent, or clear an existing mark.
- **Wages:** review each worker's attendance, advances, wage payments, and balance. The paid and pending totals open worker-by-worker breakdowns. Delete mistaken attendance or payments with confirmation.
- **Accounts:** track dated amounts that site owners or suppliers owe you, or that you owe them; edit account details and remove mistaken entries.
- **Phone transfer:** create a portable backup ZIP containing your ledger and attached proof photos, then restore it on another phone from the Accounts screen.
- **Payment proof:** attach a photo from the camera or gallery to a payment or account entry.
- **Offline storage:** records and photos are kept in the app's private storage on the device. There is no automatic cloud sync; use manual backups to move or protect your records.

## Requirements

- Android Studio with JDK 17 or later.
- Android SDK Platform 35.
- Android 7.0 (API 24) or later for installation.
- Internet access on the first build so Gradle can download the configured Gradle distribution and dependencies.

## Build

Clone the repository and open it in Android Studio, or build from a terminal:

```shell
git clone https://github.com/RAYYAN-HTML/construction_managment.git
cd construction_managment
```

```powershell
# Windows
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

```shell
# macOS or Linux
bash ./gradlew :app:assembleDebug
bash ./gradlew :app:testDebugUnitTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device with Android Debug Bridge:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds are for testing. A production release should use release signing and a stable, unique application ID.

## GitHub Actions

The Android build workflow runs for pushes, pull requests, and manual runs. It builds the debug APK and publishes it as a workflow artifact for 14 days. Open the workflow run on GitHub and download the `SiteLedger-debug-apk` artifact.

## Data and privacy

Money amounts are stored as integer minor units (paisa) in a local Room database. Database records and attached photos are kept in the app's private storage. There is no account registration or cloud sync. **Back up important records separately**: the app does not currently provide an export or restore flow, and clearing app data or uninstalling can remove locally stored records.

To move data to a new phone, choose **Accounts → Export / import data → Export backup**, save the ZIP somewhere you can access from the new phone, install SiteLedger there, then choose **Import backup** and select the ZIP. Keep backups somewhere safe: they include private financial records and photos and are **not encrypted**. Importing replaces the data currently on that phone.

On Android, **Clear cache** removes temporary cached files; it does not clear SiteLedger's database or proof photos, which are stored in private app data. **Clear storage/data** or uninstalling the app can erase them, so create and keep a backup before either action or before changing phones.

The current database schema is version 4. Upgrading a version 3 database preserves ledger data and adds configurable half-day rates. The older version 1 or 2 project/material prototype is not migrated; its local records are cleared when creating the ledger schema.

## License

SiteLedger is licensed under the MIT License. See [LICENSE](LICENSE) for details.
