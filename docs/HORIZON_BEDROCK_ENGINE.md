# Slime Bedrock Engine — development foundation

## Scope

This is an independent, original voxel-engine runtime hosted by Slime. It is
separate from the imported Mojang Bedrock APK and does not map or call
`libminecraftpe.so`, Mojang's GameActivity, or Mojang's startup lifecycle.

The first native milestone provides:

- a deterministic seeded world context;
- a 16 × 128 × 16 chunk data store;
- basic procedural height terrain with grass, dirt, stone, bedrock and sea-level water;
- block sampling and chunk statistics;
- a JNI facade callable from Kotlin;
- host-side tests for generation, determinism, bounds and reset behavior.

This is an engine foundation, not a complete Bedrock implementation. It does
not yet implement Bedrock's exact world-generation parity, mesh upload,
Vulkan renderer, collision/physics, entities, inventory, saves, networking,
or Bedrock resource/add-on formats.

## Native layout

- `app/src/main/cpp/bedrock/bedrock_engine.hpp`: engine API and chunk model.
- `app/src/main/cpp/bedrock/bedrock_engine.cpp`: deterministic terrain and chunk storage.
- `app/src/main/cpp/jni/bedrock_engine_jni.cpp`: JNI exports.
- `app/src/main/java/com/kingbrezz/slime/bedrock/BedrockEngineNative.kt`: Kotlin facade.
- `native-test/test_bedrock_engine.cpp`: host-side core tests.

The engine is linked into Slime's existing native shared library for this first
integration step. The Mojang runtime loader and preloader behavior are not
used by the engine API. A later milestone can split it into a dedicated
`libslime_bedrock.so` once the Android rendering host is introduced.

## Native test

From the repository root on a host with a C++20 compiler:

```bash
g++ -std=c++20 -Wall -Wextra -Werror \
  -Iapp/src/main/cpp/bedrock \
  app/src/main/cpp/bedrock/bedrock_engine.cpp \
  native-test/test_bedrock_engine.cpp \
  -o /tmp/slime-bedrock-engine-test
/tmp/slime-bedrock-engine-test
```

## Next implementation gates

1. Add chunk boundary neighbor sampling and greedy meshing.
2. Add an Android Surface/ANativeWindow render host and Vulkan capability checks.
3. Add camera, touch input and player collision.
4. Add persistent world storage and versioned block registry.
5. Add Bedrock-compatible network protocol and content formats only after
   their specifications and compatibility tests are available.

Do not label the current milestone as playable Bedrock. It is a tested native
world-generation core.
