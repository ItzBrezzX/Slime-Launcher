# Signature & Offset Tables

## Structure

| Kind | Count (approx) | File |
|------|----------------|------|
| Code signatures | 27 | `offset_tables.cpp` → `registerSignatures` |
| Member/vtable offsets | 60+ | `registerOffsetSlots` |
| Version keys | 26.80 / 26.70 / 26.60 / 26.40 | `registerOffsetsForVersion` |

Names aligned with Flarial `OffsetInit` / Hook targets so ports stay readable.

## Values

- **Android patterns/offsets = empty / 0 (fill them in Mod Menu → Signature / Offset)** until RE on `libminecraftpe.so` (arm64).
- **Windows Flarial numbers are not applied** (different ABI). Open dll-oss `OffsetInit.cpp` for x86_64 reference only.

## How to fill

1. Load target MC arm64 binary in IDA/Ghidra.
2. Find function → copy bytes → IDA pattern with `??` wildcards.
3. In `init_26_80` (etc.):
   ```cpp
   mgr.addSignature("Options::getGamma", "F4 0F 1E F8 ?? 00 00 91 ...");
   mgr.addOffset("ClientInstance::guiData", 0x123);
   ```
4. Rebuild `.so`, run Setelan → Init preloader, check dump (RESOLVED vs PENDING).

## JNI dump

- `PreloaderNative.dumpOffsets()` → `name=value`
- `PreloaderNative.dumpSignatures()` → `name=PENDING|RESOLVED`
