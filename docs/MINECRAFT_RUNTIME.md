# Minecraft Runtime Design

## Goal

Run the user's already-installed Minecraft Bedrock Android package inside the Slime process, so Slime's native preloader can inspect the mapped `libminecraftpe.so` image. This is an in-process game host, not an injector for the stock Minecraft process.

The original installed package remains untouched. Slime reads its installed APK/split APK files through Android's `PackageManager` and stages the native libraries into app-private storage.

## Current state

Implemented on this branch:

- Discover `com.mojang.minecraftpe` and read its version, base APK, split APKs, native-library directory and supported ABI.
- Select the ABI of the Slime process.
- Extract the currently enumerated game libraries from the installed package into Slime-private storage.
- Validate ELF machine type and file size before publishing a staged library.
- Reuse a manifest-validated cache.
- Include LeviLauncher's Apache-2.0-licensed `:minecraft` Android library module and package its `libgxcore.so` bridge binary.
- Build the Android debug APK and run the native host tests in CI.

Not implemented yet:

- A Slime-owned Activity derived from `com.mojang.minecraftpe.MainActivity`.
- A Slime runtime manager that prepares assets, storage directories and native dependencies before the game Activity calls `super.onCreate()`.
- The JNI shim used to call the packaged `gxcore` runtime bridge.
- An in-process Compose overlay for the Slime Mod Menu.
- Version-tested native hook signatures and actual detour functions.

The current Play action still runs the old demo/stock-launch path. A successful APK build is not proof of an in-process Minecraft launch.

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

Every staged ELF must match the process ABI. The manifest must include the exact Minecraft version, package update metadata, APK/split identities, ABI and required-library list. A missing dependency is a hard failure, not a warning that lets launch continue.

### 3. Resolve the native dependency graph

Do not simply call `System.load("libminecraftpe.so")` first. Use the pinned LeviLauncher runtime as the version-specific reference for dependency order.

At the pinned LeviLauncher revision `d544df8cafab731ba7686dfb88951602f13293ef`, its runtime has additional version gates:

- MAE SDK initialization begins at Minecraft 1.21.110 (with a separate beta threshold).
- HttpClient and PlayFab loading begins at Minecraft 1.21.130 (with a separate beta threshold).
- The `libMallocRedirect` path for Minecraft 1.26.70+ uses `NativeBridgeHelper.loadMinecraftRuntime()` through `libgxcore.so` before the remaining library load sequence.

The LeviLauncher app packages `libgxcore.so`, `libPlayFabMultiplayer.so` and `libmaesdk.so` as launcher-side libraries. This Slime branch currently includes the gxcore binary but explicitly excludes PlayFab and MAE. Their redistribution rights and source/provenance must be verified before bundling them. Therefore modern versions that require those libraries are not launch-ready yet.

The JNI bridge shim must use the exact class and native method contract expected by the pinned gxcore binary. Do not invent a fallback JNI symbol or mark loading successful until the real bridge call has returned successfully.

### 4. Start a same-process Minecraft Activity

Add a non-exported Activity in Slime's manifest that derives from the included `com.mojang.minecraftpe.MainActivity` class.

Before calling the Mojang Activity's `super.onCreate()`, the host must:

1. consume a fully prepared runtime session;
2. attach the AssetManager containing the installed base APK and split APK paths;
3. provide version-scoped files, data, cache and external-files directories;
4. verify all required native dependencies are already loaded in the Slime process.

Override the same storage accessors used by LeviLauncher's host (`getAssets`, `getFilesDir`, `getDataDir`, `getExternalFilesDir`, `getDatabasePath` and `getCacheDir`). Keep the activity in the default Slime process; adding an `android:process` attribute would defeat in-process loading.

Use the game Activity's real lifecycle and touch surface. Do not navigate to the demo `SessionScreen` after the launch request succeeds.

### 5. Attach Slime's overlay and preloader

After `libminecraftpe.so` is mapped in the Slime process:

- initialize `PreloaderNative` again against the loaded game image;
- require `targetFound == true` before any target scan or hook attempt;
- attach a ComposeView-based Mod Menu overlay to the game Activity;
- keep module settings and runtime state in the shared Slime repository.

The current C++ hook registry has a ShadowHook backend but its default hooks do not yet have replacement/detour function pointers registered. Signature patterns are also not validated against the installed Minecraft build. A resolved signature alone is insufficient: a hook must have a correct, typed detour and a verified original function before installation is enabled.

## State machine

`Idle -> Inspecting -> Staging -> ResolvingDependencies -> LoadingNative -> StartingActivity -> Running`

Any phase may transition to `Failed` with the exact missing package, file, ABI, dependency or native error. `Running` is set only after the host Activity reaches its ready callback and the native status confirms the game target is mapped in the same process.

A degraded preloader may allow a game session to continue without hooks, but the UI must distinguish that from a fully hook-ready session.

## Safety and compatibility rules

- No root, ptrace, cross-process memory write or modification of the installed Minecraft APK.
- No bypass of Minecraft licensing, purchase checks or account authentication.
- Do not package proprietary game libraries in the public repository; stage the user's installed copy locally.
- Do not bundle PlayFab/MAE or other launcher-side binaries until their redistribution rights are confirmed.
- Gate launch on a tested ABI/version matrix. Unknown versions must fail closed.
- Keep the original external-launch option separate from the in-process host. It must never be labelled as an injected Slime session.

## Verification checklist

1. CI: native host tests and Android debug build pass.
2. Unit tests: package/split discovery, version gates, ELF ABI checks, stale cache invalidation and missing-library errors.
3. Instrumented ARM64 device: confirm the detected version/ABI matches Android PackageManager.
4. Runtime logs: confirm each native library's load order and result.
5. Activity logs: confirm Mojang `MainActivity.onCreate()` completes and Minecraft renders its first frame.
6. Preloader: confirm `libminecraftpe.so` appears in Slime's process maps before scanning.
7. Hook tests: require a known-good signature and a registered detour for the exact tested build before reporting a hook as active.
