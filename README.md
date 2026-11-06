# Easmsy

Easmsy is a from-scratch, single-module reimplementation of the Android applications
**Island** and its reflection support library **Deagle**, both originally published by
Oasis Feng under the Apache License 2.0.

- **Application ID:** `com.easmsy`
- **App label:** Easmsy
- **APK:** `Easmsy-debug.apk` (CI artifact; debug-signed only)
- **Version:** 1.0 (versionCode 1)
- **Minimum Android version:** 7.0 (API 24)
- **Target SDK:** 29 (required by parts of the original device-policy behaviour)

The rewrite keeps the feature set and visual structure of Island. All implementation
code is new; the existing layouts, drawables, colours and strings were reconstructed or
adapted from Island's Apache-2.0 resources. See **Licensing** below.

## What it does

Easmsy is a device-policy controller for a managed profile ("Island") inside a personal
profile ("Mainland"):

- Creates and provisions the managed profile through Android's standard
  `ACTION_PROVISION_MANAGED_PROFILE` flow.
- Clones installed applications into the profile with
  `DevicePolicyManager.installExistingPackage` / `enableSystemApp`.
- Freezes and unfreezes cloned or managed apps with
  `DevicePolicyManager.setApplicationHidden`, removes them from the launcher, and
  restores them on demand.
- Supports package suspension on Android 7.0+.
- Provides the original public action protocol (`FREEZE`, `UNFREEZE`, `LAUNCH`,
  `SUSPEND`, `UNSUSPEND`) through both an activity and a broadcast receiver, with the
  original permission names and a compatibility path for known legacy clients.
- Pinned "launch in Island" shortcuts with dynamic labels and cross-profile forwarding.
- File Shuttle: a pair of content providers which expose the system external-storage
  documents provider across profiles through Android's Storage Access Framework.
- App Watcher and Island Watcher quick-control notifications.
- Greenify integration through Greenify's documented cross-app action.
- ADB Secure and per-profile App Ops management (requires a one-time ADB grant for
  `GET_APP_OPS_STATS`).
- Scoped settings, general settings, About screen and the setup wizard.

## Architecture

Everything is implemented in one Gradle module, `:app`, to reduce build time, APK size
and cross-module indirection. There is no data binding, dependency injection framework,
Firebase/Crashlytics, analytics, OkHttp, setup-wizard library, or libsuperuser.

```
app/src/main/java/com/easmsy/
├── EasmsyApplication.kt       minimal Application; notification channels only
├── MainActivity.kt            app list, tabs, search/filter, bottom-sheet actions
├── core/
│   ├── Users.kt               user/profile discovery and context creation
│   ├── DevicePolicies.kt      DevicePolicyManager facade (public API + tiny reflection)
│   ├── AppRepository.kt       on-demand app inventory and privileged actions
│   ├── AppEntry.kt            immutable app-list model
│   ├── Ops.kt                 App Ops read/write and preservation
│   ├── Prefs.kt               SharedPreferences and device-protected storage
│   ├── Notifications.kt       watcher, provisioning and install notifications
│   ├── Shortcuts.kt           pinned shortcut creation/update
│   ├── Greenify.kt            Greenify cross-app protocol
│   ├── AdbSecure.kt           DISALLOW_DEBUGGING_FEATURES toggle
│   └── Reflection.kt          the only replacement needed for Deagle
├── provision/                 admin receiver, provisioning service/activity,
│                              post-provision policies and recovery receivers
├── setup/SetupActivity.kt     reconstructed setup wizard
├── settings/                  framework PreferenceActivity screens
├── api/                       public action protocol
├── shortcut/                  pinned-shortcut launcher and updater
├── shuttle/                   cross-profile service shuttle (AIDL)
├── file/                      File Shuttle providers
├── installer/                 PackageInstaller front-end and app-info forwarding
├── watcher/                   watcher notifications and deactivation helpers
├── adb/                       profile restriction synchronisation
└── data/                      compatibility app-list provider
```

### Old module → new location

| Island / Deagle module | Original role | Easmsy location |
|---|---|---|
| `mobile` | UI, settings, app list, shortcuts | `MainActivity`, `ui/`, `settings/`, `shortcut/` |
| `engine` | provisioning and device-policy engine | `provision/`, `core/DevicePolicies.kt` |
| `shared` | users, policies, preferences, utilities | `core/` |
| `installer` | package installation and cloning | `installer/` |
| `watcher` | watcher notifications and quick controls | `watcher/`, `core/Notifications.kt` |
| `fileprovider` | File Shuttle providers | `file/` |
| `open` | public API surface | `api/` |
| `assembly` | merged application module | `:app` |
| Deagle `library` | broad reflection helper | `core/Reflection.kt` (only the needed calls) |

### Behavioural choices

- The app uses one package (`com.easmsy`) in both users. Cross-user contexts are created
  with `createPackageContextAsUser` through reflection when the public API cannot provide
  them.
- App lists are built on demand on a worker thread. There is no resident package monitor,
  no polling service and no global app cache. `MainActivity` loads icons lazily in the
  adapter.
- `MainActivity` is replaced by `IslandSettingsActivity` inside the managed profile; the
  profile-side launcher entry opens scoped settings.
- Post-provisioning is event-driven. `Provisioning.complete` is idempotent and is called
  from the admin/provisioning receivers and after package updates.
- The File Shuttle and external-storage proxy keep the original provider authorities
  relative to the new package; the system `DocumentsUI` remains the entry point.

## Feature coverage and known limitations

Island depends on a number of hidden framework APIs. The rewrite preserves those
features wherever practical and records the remaining gaps here rather than silently
removing UI.

1. **Cross-profile access** is performed with reflected
   `createPackageContextAsUser` and `getProfileOwnerAsUser`. This is the same class of
   private API used by the original project. Some ROMs may restrict it.
2. **File Shuttle** instantiates the platform external-storage provider in-process. On
   ROMs where that class is absent or changed, the provider reports unavailable instead
   of crashing. `INTERACT_ACROSS_USERS` is declared for compatibility but is normally
   granted only to privileged/system apps; without it, cross-user file forwarding may be
   refused by the platform.
3. **App Ops** (`GET_APP_OPS_STATS`) requires a one-time ADB grant. The settings screen
   explains the command. Without the grant, App Ops features are hidden or report
   incompatibility.
4. **ADB Secure in Mainland** requires device-owner mode. With only a profile owner, the
   restriction is applied to the Island profile.
5. **Installer** uses the platform `PackageInstaller`. It installs a single APK stream
   and clones packages with the device-policy APIs; it does not reimplement Island's
   detailed split-APK confirmation UI.
6. **Greenify** integration depends on the external Greenify package and its own version
   and cross-profile support.
7. **Wireless ADB / root-based provisioning** is not reimplemented; the standard managed
   profile flow is used.
8. **Some deep OEM-specific customisations** (for example vendor-specific profile
   deletion quirks) are handled with best-effort fallbacks rather than device-specific
   code paths.

These are deliberate trade-offs for a smaller, auditable implementation. No UI entry
point or core Island action was removed to make the build pass.

## Build and CI

There is deliberately **no local Gradle build** in this repository workflow. The only
supported build is the GitHub Actions workflow in `.github/workflows/build.yml`:

- JDK 17 (Temurin)
- Android SDK platform 34 and build-tools 34.0.0
- Gradle 8.7, Android Gradle Plugin 8.5.2, Kotlin 1.9.24
- `:app:assembleDebug`
- Artifact: `Easmsy-debug.apk` plus `SHA256SUMS.txt`

The debug variant is used so that the public repository contains no keystore or signing
secret. A release build must be signed outside this repository.

## Device measurement methods

The following measurements are **not** claimed by CI. CI proves that the source
compiles and that the APK is produced; it does not run the app on a device. To compare
Easmsy with the original Island build on the same hardware:

- **Idle RAM:** install both builds separately, force-stop each app, wait five minutes
  with the screen off, then read `adb shell dumpsys meminfo <package>` (PSS total) and
  `adb shell dumpsys activity processes | grep <package>`. Repeat three times and report
  the median. Do not compare while the settings UI or watcher notification is active.
- **Battery / background work:** use `adb shell dumpsys batterystats --reset`, leave the
  device idle for at least two hours with the same accounts and notifications, then run
  `adb shell dumpsys batterystats <package>` and compare partial wakelocks, jobs and
  alarms. Alternatively use Battery Historian on a bug report.
- **APK size:** compare `stat -c '%s' app-debug.apk` or the artifact size in GitHub
  Actions. For installed size use `adb shell pm path <package>` and sum the base/split
  APK sizes.
- **Cold startup:** use `adb shell am force-stop <package>`, then
  `adb shell am start -W -n <package>/<activity>` at least five times; record
  `TotalTime` and report the median. Compare the same activity and the same device state.
- **Feature verification:** provision a managed profile on a physical Android 7–14
  device (or emulator with managed-profile support), clone an app, freeze/unfreeze it,
  pin a shortcut, exercise the public API with `adb shell am broadcast`, open File
  Shuttle from DocumentsUI, and test watcher notifications.

## Licensing and attribution

Easmsy is licensed under the **Apache License, Version 2.0**. The full licence text is
in [`LICENSE`](LICENSE).

This project is a clean-room implementation, but it intentionally preserves the
Apache-2.0-licensed user-visible resources (layouts, images, colour values and strings)
of Island so that the UI remains recognisable. The original projects are:

- Island — https://github.com/oasisfeng/island — Copyright Oasis Feng and contributors,
  Apache License 2.0.
- Deagle — https://github.com/oasisfeng/deagle — Copyright Oasis Feng and contributors,
  Apache License 2.0.

No source file from those repositories is copied verbatim into the Kotlin implementation;
the reconstruction and its origins are recorded in [`NOTICE`](NOTICE). Third-party
libraries used through Gradle (AndroidX, Material Components) remain under their own
licences and are referenced by their Maven coordinates.

## Verification status

- **Proven by green GitHub Actions:** Kotlin/Java compilation, Android resource linking,
  manifest merge, debug APK assembly and artifact upload.
- **Not proven by CI:** runtime behaviour on a physical device, cross-profile file
  forwarding, provisioning on specific OEM ROMs, battery/RAM/startup measurements, and
  Greenify/ADB interaction. Those require the device tests described above.
