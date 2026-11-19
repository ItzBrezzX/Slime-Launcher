# Minecraft Runtime Integration

## Architecture

Slime's native preloader must execute in the same process that owns the
Minecraft runtime. Loading it in a separate launcher service cannot see
`libminecraftpe.so` mapped by another Android process.

LeviLauncher Android uses a launcher-owned Minecraft Activity and prepares
the selected game package/runtime before entering the game. Slime currently
does not contain that Activity, APK import path, or full game package manager.
The integration bridge below is the process-local contract for that runtime
once those parts are added.

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
- extracts only native `.so` files from `lib/<abi>/`;
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

`MinecraftRuntimeActivity` displays the imported package/version/ABI and
native-library inventory. It is currently a preparation/diagnostic screen,
not a playable Mojang game Activity. The optional "open official Minecraft"
button launches the separately installed app and does not co-load the Slime
preloader into it.

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
    // Do not claim co-load or attempt hooks.
    Log.w("SlimeRuntime", result.message)
}
```

For a separately packaged custom runtime, pass the values explicitly instead
of relying on Slime's private preferences:

```kotlin
val result = MinecraftRuntimeBridge.onRuntimeReady(
    context = applicationContext,
    versionId = selectedMinecraftVersion,
    moduleFlags = currentModuleFlags,
    moduleValues = currentModuleValues,
    signatureOverrides = signatureOverrides,
    offsetOverrides = offsetOverrides,
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

1. Prepare the selected game package and its native dependencies.
2. Load Minecraft's native runtime in the process that owns the game Activity.
3. Confirm that `libminecraftpe.so` is mapped in that process.
4. Call `MinecraftRuntimeBridge.onRuntimeReady(...)`.
5. Sync module changes with `syncFromLauncher` or `syncModules`.
6. Call `onRuntimeStopping` before tearing down the runtime.

The bridge checks the current process with the native module scanner. Calling
it from Slime's launcher process or after opening the official Minecraft app
does not attach it to that app.

## Current launcher behavior and limits

- The regular `Mainkan` action still opens the installed official Minecraft
  app by launch intent.
- The official Minecraft app is a separate Android package/process. Slime
  does not load native code into that process or bypass Android isolation.
- The old separate `:runtime` diagnostic service has been removed; it could
  not serve as the Minecraft host because it ran in a different process.
- The current repository still lacks a licensed/user-supplied game APK import
  path, a launcher-owned Minecraft Activity/runtime compatibility layer, and
  the full per-version game package preparation used by a custom runtime.
  Therefore the bridge is ready for the runtime integration point, but
  end-to-end co-load is not available in this build.
- Signature entries and offsets are placeholders until verified for the
  exact Android Minecraft build.
- The native hook registry deliberately refuses to report a hook as active
  until a real inline/trampoline backend has patched the target function.

Do not treat a successful APK build, preloader load, or module-config sync as
proof that in-game hooks are active. Verify `gameLibraryMapped`, signature
resolution, and nonzero active hooks in the actual runtime process.
