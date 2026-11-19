# Minecraft Runtime Integration

## Architecture

Slime owns a dedicated Android process for its future game host:
`MinecraftRuntimeActivity` is declared with `android:process=":game"`, so
its native libraries are mapped in `com.kingbrezz.slime:game`. The process
belongs to Slime and uses Slime's UID and private files directory. It is not
the official Minecraft app process.

The imported official Minecraft package is staged in Slime's private storage.
The native loader maps the selected package's ELF dependency graph in the
`:game` process, then loads Slime's `libslime_preloader.so` and initializes
the in-process scanner. This establishes the native process-local load path;
it does not by itself create a playable game session.

Reference architecture:
- [LeviLauncher Android source](https://github.com/LiteLDev/LeviLauncher-Android)
- [LeviLauncher Android native-mod guide](https://docs.levimc.org/LeviLauncher/android/guide/developer)

## APK import and preparation

The Versions screen supports two import paths: copy the installed official
Google Play Minecraft package (base plus split APKs) automatically, or select
a user-provided set of APK files through Android's system document picker.
For manual import, select exactly one Minecraft base APK and the corresponding
split APKs for the same release. The importer:

- checks that every archive reports package `com.mojang.minecraftpe`;
- requires the installed Minecraft package to report Google Play
  (`com.android.vending`) as its installer;
- compares the imported base APK signing certificate with the installed
  official app's current/history certificates;
- requires exactly one base APK and consistent version metadata;
- selects the ABI matching the Slime process bitness;
- extracts native `.so` files from `lib/<abi>/`;
- requires `libminecraftpe.so`, `libc++_shared.so`, `libfmod.so`, and
  `libMediaDecoders_Android.so`;
- validates ELF magic, bitness, and machine type for every extracted library;
- stops copying/extracting if app-private storage falls below the free-space guard;
- stores the APKs, native libraries, and SHA-256 checksums in Slime's private
  `files/minecraft-runtime/active` directory;
- stages a new import before replacing the previous active import, so a failed
  import does not delete the previously prepared copy.

The importer does not install the APK, modify its signature, patch it, or
bypass purchase/licensing checks. It only prepares files supplied by the user.

## Loading native libraries in :game

From `MinecraftRuntimeActivity`, tap **Muat .so ke proses :game**. The
`MinecraftNativeLibraryLoader` then:

1. Confirms the active runtime directory is Slime's private imported directory.
2. Confirms package identity, process bitness, and matching ABI.
3. Parses ELF program headers and `DT_NEEDED` entries from the imported
   libraries.
4. Builds a dependency-first load order for the private library dependencies.
5. Loads the core set (`libc++_shared.so`, `libfmod.so`,
   `libMediaDecoders_Android.so`, and `libminecraftpe.so`) with
   `System.load(absolutePath)` in the `:game` process.
6. Calls `MinecraftRuntimeBridge.onRuntimeReady(...)`, which loads
   `libslime_preloader.so` from Slime's APK and checks whether
   `libminecraftpe.so` is visible to the native process scanner.

Only libraries extracted from the validated active Minecraft package are
considered for this load plan. Android system libraries are left to the
Android linker. If loading fails after one or more libraries have been mapped,
the process cannot safely roll that mapping back; restart Slime before
retrying.

The Activity reports the process name/PID, ABI, loaded library names,
preloader state, signature counts, and active hook count. A mapped
`libminecraftpe.so` is evidence that the image is present in `:game`; it is
not evidence that the Mojang game has initialized or is playable.

## Bridge API

`MinecraftRuntimeBridge` must be called by the Activity/Application that owns
the game runtime, after its normal loader has mapped `libminecraftpe.so`.

### Start with saved Slime settings

When the runtime belongs to the same Slime application/package, use the
configuration provider. It reads the active version, module flags, numeric
settings, signature overrides, and offset overrides from the same private
launcher preferences:

```kotlin
val result = MinecraftRuntimeBridge.onRuntimeReady(applicationContext)

if (!result.gameLibraryMapped) {
    Log.w("SlimeRuntime", result.message)
}
```

For the current imported runtime, pass its actual package version:

```kotlin
val result = MinecraftRuntimeBridge.onRuntimeReady(
    context = applicationContext,
    versionId = importedRuntime.manifest.versionName,
)
```

The runtime APK must package `libslime_preloader.so` for the ABI of its
process. The bridge loads it from that runtime application's
`nativeLibraryDir`.

### Live settings updates

For a same-package runtime, pull updated toggles and numeric settings after
the launcher UI changes them:

```kotlin
MinecraftRuntimeBridge.syncFromLauncher(applicationContext)
```

A runtime with its own configuration source can call
`MinecraftRuntimeBridge.syncModules(moduleFlags, moduleValues)` directly.

Signature/offset overrides are applied during initialization. After changing
those values, initialize the bridge again at a safe runtime boundary so the
native signature table is rebuilt for the selected version.

### Shutdown

Call this from the owning runtime's normal shutdown path:

```kotlin
MinecraftRuntimeBridge.onRuntimeStopping()
```

## Required call order

1. Import and validate the official Minecraft package and matching ABI.
2. Start the Slime-owned `:game` process.
3. Map the Minecraft ELF dependencies and `libminecraftpe.so` in that process.
4. Load and initialize Slime's preloader with `MinecraftRuntimeBridge`.
5. Enter the compatible Minecraft Java/GameActivity lifecycle (not implemented yet).
6. Sync module settings and shut down the preloader from the owning runtime lifecycle.

The bridge checks the current process with the native module scanner. Calling
it from the launcher UI process or after opening official Minecraft does not
attach it to the official app.

## Current launcher behavior and limits

- The regular `Mainkan` action still opens the installed official Minecraft
  app by launch intent.
- The official Minecraft app is a separate Android package/process. Slime
  does not load native code into that process or bypass Android isolation.
- `MinecraftRuntimeActivity` now runs in Slime's dedicated `:game` process
  and can map the imported native library dependency graph there.
- The `:game` Activity remains a diagnostic host, not Mojang's
  `com.mojang.minecraftpe.MainActivity`. The compatible Java Activity,
  asset/resource context, game storage setup, and full startup/shutdown
  lifecycle are not implemented yet. Therefore this build does not start a
  playable Minecraft session inside Slime.
- Some Minecraft releases have version-specific bootstrap libraries and
  startup requirements beyond the core ELF dependency graph. The new loader
  does not claim full version-specific game initialization.
- Signature entries and offsets are placeholders until verified for the
  exact Android Minecraft build.
- The native hook registry deliberately refuses to report a hook as active
  until a real inline/trampoline backend has patched the target function.

Do not treat successful ELF mapping, preloader initialization, or
module-config synchronization as proof that in-game hooks are active or
that the game has started. Verify the process name, `gameLibraryMapped`,
signature resolution, active hooks, and eventually the real game Activity
lifecycle on a device.
