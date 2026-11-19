# native-out

Pre-built `.so` files were removed in 1.5.0: they predated the new JNI exports and would
clash with the library Gradle builds from `app/src/main/cpp` (CMake, NDK r27).

The APK always contains a fresh `libslime_preloader.so` per ABI
(arm64-v8a, armeabi-v7a, x86_64) built by `./gradlew assembleDebug`.
CI also uploads them as the `slime-native-libs` artifact.

Native logic is verified on the host by `native-test/run.sh` (see docs/PRELOADER.md).
