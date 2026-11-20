# Slime Bedrock Engine — development foundation

## Milestone 0.2: chunk mesh and memory

The independent C++ engine now stores block IDs as uint8_t (32 KiB per 16×128×16 chunk), builds indexed face meshes, and samples loaded neighboring chunks when deciding whether a boundary face is visible. Visible-face diagnostics use the same neighbor-aware rule as the generated mesh, and tests assert that both counts match. Missing neighboring chunks are treated as air so the boundary is initially emitted; after a neighbor loads, rebuilding the adjacent chunk removes internal boundary faces.

Mesh vertices contain seven values: local position (x/y/z), normal (x/y/z), and block ID. Indices are 32-bit and grouped into two triangles per visible face. JNI exposes vertex/index buffers and storage-byte counters to Kotlin. The mesh is CPU-side only; no GPU upload or renderer is included yet.

## Scope and limitations

This is an original voxel-engine foundation hosted by Slime, independent from Mojang's runtime. It does not load libminecraftpe.so or invoke Mojang startup. Terrain is deterministic but not Bedrock-parity. There is no Vulkan renderer, camera, collision/physics, entities, inventory, persistence, networking, or Bedrock resource/add-on compatibility yet.

## Native tests

Run `bash native-test/run.sh` with a C++20 host compiler. Tests cover deterministic generation, reset, compact chunk storage, mesh output, and neighbor-boundary face reduction.
