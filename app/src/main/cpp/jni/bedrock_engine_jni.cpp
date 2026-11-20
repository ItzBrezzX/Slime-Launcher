#include <jni.h>
#include <cstdint>
#include "bedrock_engine.hpp"

namespace {
slime::bedrock::Engine gEngine;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeCreateWorld(
    JNIEnv*, jclass, jlong seed) {
    gEngine.createWorld(static_cast<int64_t>(seed));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeGenerateChunk(
    JNIEnv*, jclass, jint chunkX, jint chunkZ) {
    return gEngine.generateChunk(chunkX, chunkZ) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeGetBlock(
    JNIEnv*, jclass, jint worldX, jint y, jint worldZ) {
    return static_cast<jint>(gEngine.blockAt(worldX, y, worldZ));
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeGetChunkStats(
    JNIEnv* env, jclass, jint chunkX, jint chunkZ) {
    const auto stats = gEngine.chunkStats(chunkX, chunkZ);
    const jlong values[5] = {
        static_cast<jlong>(stats.solidBlocks),
        static_cast<jlong>(stats.waterBlocks),
        static_cast<jlong>(stats.visibleFaces),
        static_cast<jlong>(stats.minHeight),
        static_cast<jlong>(stats.maxHeight),
    };
    jlongArray result = env->NewLongArray(5);
    if (result) env->SetLongArrayRegion(result, 0, 5, values);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeLoadedChunkCount(
    JNIEnv*, jclass) {
    return static_cast<jint>(gEngine.loadedChunkCount());
}

extern "C" JNIEXPORT void JNICALL
Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeReset(
    JNIEnv*, jclass) {
    gEngine.reset();
}
