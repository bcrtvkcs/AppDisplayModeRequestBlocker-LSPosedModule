# Display Mode Request Blocker

An LSPosed / Xposed module that stops apps from forcing a specific display mode, so they cannot silently switch the screen to a lower resolution.

## The Problem

Some apps set `WindowManager.LayoutParams#preferredDisplayModeId` on their window. Android's `DisplayModeDirector` turns that request into a `PRIORITY_APP_REQUEST_SIZE` vote, and this vote wins over the user's preferred resolution.

Example (OnePlus 9 Pro, 1440x3216 panel with a custom `wm density` override): opening such an app switches the panel to 1080x2412. The physical density drops from 560 to 420 while the override stays the same, so the whole UI, including the status bar and quick settings, becomes about 33% larger until the app is closed.

## How It Works

The module hooks `AppRequestObserver.setAppRequest(int displayId, int modeId, ...)` inside the system framework and replaces the requested `modeId` with `0` ("no mode requested"). With no mode id, `DisplayModeDirector` never creates the size vote, so the display stays at the resolution you chose.

The hook tries these class locations, because the observer moved between Android releases:

- `com.android.server.display.mode.DisplayModeDirector$AppRequestObserver`
- `com.android.server.display.mode.AppRequestObserver`
- `com.android.server.display.DisplayModeDirector$AppRequestObserver`

### Limitations

- Every app's `preferredDisplayModeId` request is ignored, not only the ones that lower the resolution. Other refresh-rate requests (such as `preferredRefreshRate`) are not touched.
- Hooking framework internals can break when Android changes them. If the class or method is not found, the module logs it and does nothing.

## Requirements

- Android 10+ (API 29)
- Root access (Magisk / KernelSU)
- LSPosed or Xposed Framework

## Installation

1. Download `dist/AppDisplayModeRequestBlocker-LSPosedModule.apk` from this repository (or the artifact of the latest Actions run) and install it.
2. Open LSPosed Manager and enable **Display Mode Request Blocker**.
3. Make sure **System Framework** is selected in the module scope.
4. Reboot the device.

## Verification

After the reboot, open the affected app and run:

```
su -c "dumpsys display | grep PRIORITY_APP_REQUEST_SIZE"
```

No output means no app size vote exists. The resolution should also stay unchanged:

```
su -c "wm size"
```

The module logs to the LSPosed log under the tag `DisplayModeRequestBlocker`. It reports which class and method it hooked at boot, and every request it ignores.

## Building

The APK is built by GitHub Actions on every push to `main` (see `.github/workflows/build.yml`). The result is available as a workflow artifact and is also committed to `dist/`.

To build locally you need JDK 17, the Android SDK (platform 36) and Gradle 8.14.4:

```
gradle assembleRelease
```

The release build is signed with the debug-grade key in `app/debug.keystore`, so updates install over previous builds. It is meant for personal sideloading only.

## Project Structure

```
app/src/main/
├── java/com/displaymodeblocker/
│   └── MainHook.java        # The hook
├── assets/xposed_init       # Module entry class
├── res/values/
│   ├── arrays.xml           # Recommended scope (system framework)
│   └── strings.xml          # Module name and description
└── AndroidManifest.xml      # Xposed metadata
```
