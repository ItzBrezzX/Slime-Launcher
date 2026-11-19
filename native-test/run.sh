#!/usr/bin/env bash
# Host-side verification of the NDK code (no Android SDK needed).
#  - builds libslime_preloader.so with the same flags as CMake (-Werror)
#  - test_main:  self-test, JNI layer (fake JNIEnv), overrides, scanner, effects
#  - test_ready: loads a fake libminecraftpe.so in-process → phase Ready, hook IDLE→FAILED(honest)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../app/src/main/cpp"
OUT="$HERE/build"
mkdir -p "$OUT"
cd "$OUT"

FLAGS="-std=c++20 -fno-exceptions -fno-rtti -fvisibility=hidden -Wall -Wextra -Werror -I$HERE/stubs -I$SRC/include -O1 -g"

g++ $FLAGS -fPIC -shared "$SRC"/src/*.cpp "$SRC/jni/jni_bridge.cpp" -o libslime_preloader.so
g++ $FLAGS "$HERE/test_main.cpp" -L. -lslime_preloader -Wl,-rpath,"$OUT" -o test_main
gcc -shared -fPIC "$HERE/fakemc.c" -o libminecraftpe.so
g++ -std=c++20 -I"$HERE/stubs" -I"$SRC/include" "$HERE/test_ready.cpp" -L. -lslime_preloader -ldl -Wl,-rpath,"$OUT" -o test_ready

./test_main 2>/dev/null
SLIME_FAKE_MC="$OUT/libminecraftpe.so" ./test_ready 2>/dev/null
echo "NATIVE TESTS OK"
