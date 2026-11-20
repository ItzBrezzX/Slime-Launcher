# Minecraft Runtime Design

## Goal

Run the user's already-installed Minecraft Bedrock Android package inside the Slime process, so Slime's native preloader can inspect the mapped `libminecraftpe.so` image. This is an in-process game host, not an injector for the stock Minecraft process.

The original installed package remains untouched. Slime reads its installed APK/split APK files through Android's `PackageManager` and stages the native libraries into app-private storage.

## Current state

Implemented on this branch:

- Discover `com.mojang.minecraftpe` and read its version, base APK, split APKs, native-library directory and supported ABI.
- Extract the currently enumerated game libraries into Slime-private storage, validate ELF machine type and reuse a manifest-validated cache.
- Include LeviLauncher's Apache-2.0-licensed `:minecraft` Android library module at the pinned submodule revision.
- Add `MinecraftActivity`, derived from `com.mojang.minecraftpe.MainActivity`.
- Add `MinecraftActivitySession` to combine the installed base/split APK assets with Slime's resources and provide version/ABI-scoped files, data, external-files, cache and database directories.
- Align the app's AndroidX Games Activity, Activity/Lifecycle and Java/Kotlin bytecode targets with the pinned runtime module.

Not implemented yet:

- The version-specific native dependency loader and the JNI shim used by the pinned `gxcore` bridge.
- Enabling/launching `MinecraftActivity`. Its manifest component is intentionally `enabled=false` until native libraries and the GameActivity native entry point are loaded and verified.
- The in-process Slime Mod Menu overlay attached to the game Activity.
- Version-tested native hook signatures and actual detour functions.
- Device verification that Minecraft reaches its first rendered frame inside Slime.

The Play action still follows the existing launcher/demo path. A successful APK build is not proof of an in-process Minecraft launch.

## Build compatibility contract

The app and the pinned `:minecraft` module use:

| Setting | Value |
|---|---|
| compile SDK | 36 |
| min SDK | 26 |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 |
| Kotlin | 2.3.0 |
| Java/Kotlin bytecode target | 21 |
| AndroidX Games Activity | 4.4.2 |

The app declares `androidx.games:games-activity:4.4.2` directly because the Mojang Activity's public superclass is AndroidX `GameActivity`, while the upstream library module declares that dependency as `implementation`. Activity and Lifecycle versions are aligned with the versions catalog in the pinned LeviLauncher revision. CI runs on JDK 21.

## Runtime phases

### 1. Inspect the installed package

Use Android `PackageManager` to obtain:

- package name, version name and version code;
- base APK and all public split APK paths;
- native library directory and install/update metadata;
- ABI supported by the running Slime process.

Reject a package when the base APK or required ABI-specific game library cannot be read. Do not guess the game version from the Slime version selector.

### 2. Stage and validate libraries

Stage into a version- and ABI-specific directory under `filesDir`. Keep the install source read-only and publish staged files atomically from temporary files.

The current source enumerates these game libraries:

- `libc++_shared.so`
- `libfmod.so`
- `libMediaDecoders_Android.so`
- `libMallocRedirect.so` for versions at or above the current threshold in `MinecraftInstalledSource`
- `libminecraftpe.so`
- `libHttpClient.Android.so` for versions at or above the current threshold in `MinecraftInstalledSource`

Every staged ELF must match the process ABI. A missing required dependency is a hard failure, not a warning that lets launch continue.

### 3. Resolve the native dependency graph

Do not simply call `System.load("libminecraftpe.so")` first. Use the pinned LeviLauncher runtime as the version-specific reference for dependency order.

At pinned LeviLauncher revision `d544df8cafab731ba7686dfb88951602f13293ef`, its runtime has additional version gates:

- MAE SDK initialization begins at Minecraft 1.21.110 (with a separate beta threshold).
- HttpClient and PlayFab loading begins at Minecraft 1.21.130 (with a separate beta threshold).
- The `libMallocRedirect` path for Minecraft 1.26.70+ uses `NativeBridgeHelper.loadMinecraftRuntime()` through `libgxcore.so` before the remaining library load sequence.

The LeviLauncher app packages `libgxcore.so`, `libPlayFabMultiplayer.so` and `libmaesdk.so` as launcher-side libraries. This Slime branch currently includes the gxcore binary but explicitly excludes PlayFab and MAE. Their redistribution rights and source/provenance must be verified before bundling them. Therefore modern versions that require those libraries are not launch-ready yet.

The JNI bridge shim must use the exact class and native method contract expected by the pinned gxcore binary. Do not invent a fallback JNI symbol or mark loading successful until the real bridge call has returned successfully.

### 4. Start a same-process Minecraft Activity

The Activity is now implemented at `app/src/main/java/com/kingbrezz/slime/runtime/MinecraftActivity.kt` and is declared non-exported in the app manifest.

Before the Mojang Activity's `super.onCreate()`, the runtime bootstrap must:

1. inspect the installed package and call `MinecraftActivitySession.prepare(context, info)`;
2. load and verify every required native dependency in the Slime process;
3. provide the verified GameActivity native entry-point metadata;
4. enable the `MinecraftActivity` manifest component and launch it in the default Slime process.

The session attaches the installed base and split APK paths to an AssetManager, followed by Slime's own APK paths. It creates isolated version/ABI-scoped storage directories and overrides `getAssets()`, `getFilesDir()`, `getDataDir()`, `getExternalFilesDir()`, `getDatabasePath()` and `getCacheDir()` before Mojang's Activity lifecycle begins.

**Current gate:** the manifest has `android:enabled="false"` on `MinecraftActivity`. Do not enable it before the native loader stage supplies the correct `android.app.lib_name` contract and confirms the game dependencies are loaded. The Activity is assigned to the existing `:game` process so it shares the address space with `MinecraftNativeLibraryLoader`. Session preparation must execute inside that process.

### 5. Attach Slime's overlay and preloader

After `libminecraftpe.so` is mapped in the Slime process:

- initialize `PreloaderNative` against the loaded game image;
- require `targetFound == true` before any target scan or hook attempt;
- attach a ComposeView-based Mod Menu overlay to the game Activity;
- keep module settings and runtime state in the shared Slime repository.

The current C++ hook registry has a ShadowHook backend but its default hooks do not yet have replacement/detour function pointers registered. Signature patterns are also not validated against the installed Minecraft build. A resolved signature alone is insufficient: a hook must have a correct, typed detour and a verified original function before installation is enabled.

## State machine

`Idle -> Inspecting -> Staging -> ResolvingDependencies -> LoadingNative -> StartingActivity -> Running`

Any phase may transition to `Failed` with the exact missing package, file, ABI, dependency or native error. `Running` is set only after the host Activity reaches its ready callback and native status confirms the game target is mapped in the same process.

A degraded preloader may allow a game session to continue without hooks, but the UI must distinguish that from a fully hook-ready session.

## Safety and compatibility rules

- No root, ptrace, cross-process memory write or modification of the installed Minecraft APK.
- No bypass of Minecraft licensing, purchase checks or account authentication.
- Do not package proprietary game libraries in the public repository; stage the user's installed copy.
- Do not bundle PlayFab/MAE or other launcher-side binaries until their redistribution rights are confirmed.
- Gate launch on a tested ABI/version matrix. Unknown versions must fail closed.
- Keep the original external-launch option separate from the in-process host. It must never be labelled as an injected Slime session.

## Verification checklist

1. CI native host tests and Android debug build pass.
2. Unit tests cover package/split discovery, version gates, ELF ABI checks, stale cache invalidation and missing-library errors.
3. On an ARM64 device, confirm detected version/ABI matches Android PackageManager.
4. Runtime logs confirm each native library's load order and result.
5. Activity logs confirm Mojang `MainActivity.onCreate()` completes and Minecraft renders its first frame.
6. Confirm `libminecraftpe.so` appears in Slime's process maps before scanning.
7. Hook tests require a known-good signature and a registered detour for the exact tested build before reporting a hook as active.
