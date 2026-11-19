# Slime Preloader + NDK

## Architecture (studied from)

| Component | Source pattern | Slime file |
|-----------|----------------|------------|
| Signature + offset maps | Flarial SignatureAndOffsetManager | signature_manager.* |
| Per-version offset init | Flarial OffsetInit::initXXXX | offset_tables.cpp |
| Hook list / resolve | Flarial Client/Hook | hook_registry.* |
| Module enable bridge | Flarial ModuleManager | module_bridge.* |
| Load SO into context | Levi ModNativeLoader / System.load | PreloaderNative.kt |
| Init pipeline phases | Levi preloader flow | preloader.cpp |

## Pipeline

```
Play / Setelan → Init preloader
  → LoadOffsets(versionId)
  → ScanSignatures(moduleBase)   // needs game image
  → ResolveHooks
  → Ready | Degraded
```

## Critical honesty

1. Patterns in offset_tables.cpp are EMPTY placeholders.
   Real IDA/Ghidra signatures must be filled per Minecraft build.
   Empty patterns → 0 resolved → hooks stay Pending.

2. Install is gated. tryInstall returns false without resolved addresses
   and in-process co-load with the game (Levi model: load .so into game process).

3. Without NDK build, System.loadLibrary fails → Java UI still works;
   status shows native library not loaded.

## Filling signatures

1. Open target MC arm64 binary in IDA/Ghidra.
2. Locate functions (ClientInstance update, gamma, FOV, etc.).
3. Put IDA-style pattern into init_26_80 / init_26_70 in offset_tables.cpp.
4. Rebuild APK, check logcat tag: SlimePreloader.

## Build

```bash
./gradlew assembleDebug
# libslime_preloader.so inside APK lib/<abi>/
```

## Live inject pipeline (`InjectEngine`)

```
Mainkan
  → PrepareNative (load libslime_preloader.so)
  → InitPreloader(versionId)
  → SyncModules (flags → native)
  → ProbeTarget (com.mojang.minecraftpe)
  → LaunchGame (intent, if enabled)
  → AttachAttempt
  → Done + summary on session HUD
```

### What "live inject" means here

| Platform | Typical mechanism |
|----------|-------------------|
| Windows Flarial | DLL inject + MinHook into MC process |
| Android Levi | Co-load `.so` inside game/runtime process |
| **Slime (this build)** | Pipeline + native ready; **stock MC is separate process** |

Cross-process ptrace inject into stock Minecraft is **not** performed.
Without resolved signatures, status stays **Degraded**.
Without a co-load host (custom runtime / mod entry that loads our `.so`), attach stays false even if signatures were filled.

Fill `offset_tables.cpp` patterns → rebuild `.so` → co-load host required for true in-process hooks.


## 1.5.0 — what changed

| Area | Change |
|------|--------|
| Scan | `ScanSignatures` now finds `libminecraftpe.so` in the **current process** (`dl_iterate_phdr`) and scans only its readable `PT_LOAD` segments. Not mapped → Degraded with an explicit message. |
| Patterns | Parser is exception-free (`-fno-exceptions` + user input): 2 hex digits per byte or `??`, max 512 bytes. Invalid → error string, never a crash. |
| Overrides | `SignatureManager::setOverrideSignature / setOverrideOffset` survive table reloads. Java persists them and re-pushes on start. No rebuild needed to fill patterns. |
| Modules | `ModuleBridge` is thread-safe and carries numeric values (`zoom.level`, `fullbright.gamma`, `fog.density`, `time.hour`, `viewmodel.scale`). |
| Effects | `effects.hpp`: pure functions a hook detour calls with the original value (`fov`, `gamma`, `fogDistance`, `timeTicks`, `viewmodelScale`). Disabled module → value untouched. |
| Hooks | `HookDesc.reason` explains each state. `installAll()` added. |
| Tools | `nativeScanModule`, `nativeListProcessModules`, `nativeSelfTest`, `nativeEvalEffect`, dumps for hooks/signatures/offsets. |

## Hook backend — still missing (by design)

`tryInstall` never reports `Active`: no trampoline/inline-hook backend is linked, and stock Minecraft runs in a
separate process. With a resolved target the hook goes `IDLE → FAILED("backend belum tersedia")`.
Next real step: add an ARM64 trampoline backend and a co-load entry (Levi-style) so the `.so` runs inside the game process.

## Verification

```bash
bash native-test/run.sh   # host build with -Werror, JNI via fake JNIEnv, fake libminecraftpe.so → Ready path
```

CI runs it before `assembleDebug`. The Kotlin/Compose side is compiled only by Gradle (CI).


## Native Runtime Loader

```text
InjectEngine
  -> NativeRuntimeLoader.inspect(applicationInfo.nativeLibraryDir)
  -> Detect device ABI + enumerate packaged .so files
  -> Validate ELF magic, ELF class, and e_machine
  -> System.load(absolute path to libslime_preloader.so)
  -> PreloaderNative JNI calls
```

The loader reports the selected ABI, supported device ABIs, native library directory,
each packaged library's size and ELF compatibility, and any load error. A missing or
incompatible preloader fails the pipeline with an explicit diagnostic rather than
claiming native readiness.

Android's dynamic linker resolves each shared object's declared DT_NEEDED dependencies
when the library is loaded. Slime therefore does not manually load platform libraries
such as libc, liblog, or libandroid, and does not guess a dependency order. The current
APK packages the preloader as the native entry point; no separate Minecraft runtime
or game-process co-load library is included.

This loader only loads Slime's library into the launcher process. It does not load
libraries into stock Minecraft's separate process or bypass Android process isolation.


## Runtime integration bridge

`MinecraftRuntimeBridge` is the entry point for a custom runtime that owns
the Minecraft process. Call `onRuntimeReady` only after that runtime has
mapped `libminecraftpe.so`; the call loads Slime's preloader in the current
process, initializes the selected build, and syncs module/signature/offset
configuration. Use `syncModules` for live settings changes and
`onRuntimeStopping` during normal game shutdown.

`installHooks` checks that the target game library is mapped in the calling
process and that signatures are resolved. It still cannot create an active
hook until the ARM64 trampoline backend is implemented.

The Android manifest's private `:runtime` service is a Slime-owned diagnostic
process. It is not the Minecraft process and must not be used as an attach
target. The launcher pipeline now checks the native state in its own process
and reports the official Minecraft app as separate.

The current repository does not yet include a Minecraft APK import flow, a
launcher-owned Minecraft Activity/runtime compatibility layer, or the full
game runtime package manager. The bridge API is the in-process seam for that
next integration; it does not by itself launch or co-load the stock game.

See [RUNTIME_INTEGRATION.md](RUNTIME_INTEGRATION.md) for call order and the
remaining prerequisites.
