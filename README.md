# SiteLedger

SiteLedger is an offline-first Android app for tracking construction-site labour, wages, advances, and balances with site owners and suppliers.

## Features

- Keep a worker register with full-day and half-day wage rates.
- Record monthly attendance in a spreadsheet-style sheet with a fixed worker-name column and horizontally scrollable days. Mark, edit, or clear present, half-day, and absent records; saved wage rates remain tied to each attendance date.
- Review each worker's attendance, advances, wage payments, and running balance.
- Open paid and pending wage summaries to see per-worker totals.
- Record dated account entries showing whether a site owner or supplier owes you or you owe them.
- Edit worker and account details, and delete mistaken attendance or ledger entries with confirmation.
- Attach a photo from the camera or gallery as proof for a payment or account entry.
- Store data on the device and work offline. There is no in-app cloud sync or export/restore feature.

## Requirements

- Android Studio with JDK 17 or newer.
- Android SDK Platform 35.
- Android 7.0 (API 24) or newer to install the app.

The Gradle wrapper and dependency versions are included. The first build needs internet access to download Gradle and dependencies.

## Build and install

Open the repository in Android Studio, let Gradle sync, then run the `app` configuration on a device or emulator.

To build a debug APK from a terminal:

```shell
# Windows
gradlew.bat :app:assembleDebug

# macOS or Linux
bash ./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To install it on a connected device with Android Debug Bridge:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow builds the debug APK for pushes and pull requests and makes it available as a downloadable workflow artifact. Debug APKs are for testing; publishing a production release requires release signing and a stable, unique application ID.

## Data and migrations

The Room database stores money in integer minor units (paisa). Photos and database records are stored in the app's private device storage. Removing a ledger entry also removes its attached proof photo.

The current database schema is version 4. Upgrading a version 3 database preserves ledger data and adds configurable half-day rates. Upgrading the older version 1 or 2 project/material prototype clears its local records when creating the ledger schema; those old records are not migrated.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
