#!/usr/bin/env bash
# Host-side verification of the NDK code (no Android SDK needed).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../app/src/main/cpp"
OUT="$HERE/build"
mkdir -p "$OUT"
cd "$OUT"

FLAGS="-std=c++20 -fno-exceptions -fno-rtti -fvisibility=hidden -Wall -Wextra -Werror -I$HERE/stubs -I$SRC/include -I$SRC/bedrock -O1 -g"

g++ $FLAGS -fPIC -shared "$SRC"/src/*.cpp "$SRC"/bedrock/*.cpp "$SRC"/jni/jni_bridge.cpp "$SRC"/jni/bedrock_engine_jni.cpp "$SRC"/jni/vulkan_backend_jni.cpp -o libslime_preloader.so
g++ $FLAGS "$HERE/test_main.cpp" -L. -lslime_preloader -Wl,-rpath,"$OUT" -o test_main
gcc -shared -fPIC "$HERE/fakemc.c" -o libminecraftpe.so
g++ -std=c++20 -I"$HERE/stubs" -I"$SRC/include" "$HERE/test_ready.cpp" -L. -lslime_preloader -ldl -Wl,-rpath,"$OUT" -o test_ready
g++ -std=c++20 -Wall -Wextra -Werror -I"$SRC/bedrock" "$SRC/bedrock/bedrock_engine.cpp" "$HERE/test_bedrock_engine.cpp" -o test_bedrock_engine

./test_main 2>/dev/null
SLIME_FAKE_MC="$OUT/libminecraftpe.so" ./test_ready 2>/dev/null
./test_bedrock_engine
g++ $FLAGS "$SRC/bedrock/vulkan_backend.cpp" "$HERE/test_vulkan_backend.cpp" -o test_vulkan_backend
./test_vulkan_backend
echo "NATIVE TESTS OK"
