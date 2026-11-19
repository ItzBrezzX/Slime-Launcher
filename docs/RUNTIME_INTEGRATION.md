# Minecraft Runtime Integration

## Goal

Slime's native preloader must execute inside the process that owns Minecraft's
runtime. Loading the preloader in the launcher process or in Slime's separate
`:runtime` service does not make `libminecraftpe.so` visible there.

LeviLauncher Android follows a different architecture from simply opening the
installed Minecraft package: it prepares the selected game APK/runtime and
enters Minecraft through a launcher-owned Activity. Its Android native mods are
then loaded in the game process. See the public
[LeviLauncher Android project](https://github.com/LiteLDev/LeviLauncher-Android)
and its [Android native-mod guide](https://docs.levimc.org/LeviLauncher/android/guide/developer).

## Slime bridge API

`MinecraftRuntimeBridge` is the in-process integration point:

- `onRuntimeReady(...)`: loads Slime's preloader in the calling process,
  initializes the selected version, and pushes module/signature/offset settings.
- `syncModules(...)`: applies live module toggles and numeric values.
- `installHooks()`: refuses to proceed unless `libminecraftpe.so` is mapped
  in the calling process and signatures are resolved.
- `onRuntimeStopping()`: resets Slime's native runtime state.

The custom runtime must package `libslime_preloader.so` for the same ABI as its
own process and include the bridge source (or a future published Slime runtime
SDK). The bridge loads the library from that runtime application's native
library directory.

## Required call order

The runtime host must invoke the bridge only after its normal Minecraft library
loader has mapped `libminecraftpe.so`:

```kotlin
// Called by the custom runtime's engine-ready callback, not by the launcher.
val result = MinecraftRuntimeBridge.onRuntimeReady(
    context = applicationContext,
    versionId = selectedMinecraftVersion,
    moduleFlags = currentModuleFlags,
    moduleValues = currentModuleValues,
    signatureOverrides = signatureOverrides,
    offsetOverrides = offsetOverrides,
)

if (!result.gameLibraryMapped) {
    // Do not claim co-load or attempt hooks. Check process and load order.
    log(result.message)
}
```

When the runtime changes module configuration:

```kotlin
MinecraftRuntimeBridge.syncModules(
    moduleFlags = updatedFlags,
    moduleValues = updatedValues,
)
```

When the runtime is shutting down normally:

```kotlin
MinecraftRuntimeBridge.onRuntimeStopping()
```

The callback is process-local. Calling it from Slime's launcher Activity,
a separate Android service, or after launching the official Minecraft app
does not attach it to that app.

## Current launcher behavior

- The normal `Mainkan` action can still open the installed official Minecraft
  app through its launch intent.
- The official app is a separate Android package/process. Slime does not claim
  to load its native library into that process.
- The manifest's `:runtime` service is a Slime-owned diagnostic/IPC process.
  It is not the Minecraft runtime and is no longer used as the attach target
  by `InjectEngine`.
- The repository does not yet contain a Minecraft APK import/runtime Activity,
  game Activity compatibility layer, or runtime package manager. Therefore
  the bridge API is ready for a custom runtime integration, but end-to-end
  co-load is not yet available in this launcher build.

## Remaining prerequisites for live hooks

1. Add a licensed, user-supplied Minecraft APK import/version runtime path.
2. Add the game Activity/runtime compatibility layer so Minecraft runs under
   the Slime-owned runtime process, rather than launching the stock package.
3. Package `libslime_preloader.so` into that runtime and call the bridge after
   `libminecraftpe.so` is mapped.
4. Populate and validate signatures for each supported game build.
5. Implement and test a real ARM64 hook/trampoline backend. The current native
   hook registry deliberately refuses to report a hook as active when no
   backend has patched the target function.

Do not treat a successful APK build, preloader load, or module-config sync as
proof that in-game hooks are active. Verify `gameLibraryMapped`, signature
resolution, and nonzero active hooks in the actual runtime process.
