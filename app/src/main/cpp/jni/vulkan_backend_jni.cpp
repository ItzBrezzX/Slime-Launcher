#include <jni.h>
#include <android/native_window_jni.h>
#include <cstdio>
#include <cstring>
#include <limits>
#include <vector>
#include "vulkan_backend.hpp"

namespace {
slime::bedrock::VulkanBackend gVulkanBackend;

std::vector<uint32_t> readSpirv(JNIEnv* env, jbyteArray bytes) {
    std::vector<uint32_t> words;
    if (!bytes) return words;
    const jsize length = env->GetArrayLength(bytes);
    if (length <= 0 || (length % 4) != 0) return words;
    words.resize(static_cast<size_t>(length) / sizeof(uint32_t));
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(words.data()));
    if (env->ExceptionCheck()) return {};
    if (words.empty() || words[0] != 0x07230203u) return {};
    return words;
}
jstring makeString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kingbrezz_slime_bedrock_VulkanBackendNative_nativeAttachSurface(
    JNIEnv* env, jclass, jobject surface, jbyteArray vertexBytes, jbyteArray fragmentBytes) {
    if (!surface) return makeString(env, "Android Surface is null.");
    auto vertex = readSpirv(env, vertexBytes);
    auto fragment = readSpirv(env, fragmentBytes);
    if (vertex.empty() || fragment.empty())
        return makeString(env, "SPIR-V assets are missing, invalid, or not 4-byte aligned.");
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) return makeString(env, "ANativeWindow_fromSurface failed.");
    return makeString(env, gVulkanBackend.attachSurface(window, vertex, fragment));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kingbrezz_slime_bedrock_VulkanBackendNative_nativeSetMesh(
    JNIEnv* env, jclass, jfloatArray vertexArray, jintArray indexArray) {
    if (!vertexArray || !indexArray) return JNI_FALSE;
    const jsize floatCount = env->GetArrayLength(vertexArray);
    const jsize indexCount = env->GetArrayLength(indexArray);
    if (floatCount <= 0 || floatCount % 7 != 0 || indexCount <= 0) return JNI_FALSE;
    std::vector<jfloat> floats(static_cast<size_t>(floatCount));
    std::vector<jint> rawIndices(static_cast<size_t>(indexCount));
    env->GetFloatArrayRegion(vertexArray, 0, floatCount, floats.data());
    env->GetIntArrayRegion(indexArray, 0, indexCount, rawIndices.data());
    if (env->ExceptionCheck()) return JNI_FALSE;
    std::vector<slime::bedrock::MeshVertex> vertices(static_cast<size_t>(floatCount / 7));
    for (size_t i = 0; i < vertices.size(); ++i) {
        const size_t p = i * 7;
        vertices[i] = {
            floats[p], floats[p + 1], floats[p + 2],
            floats[p + 3], floats[p + 4], floats[p + 5],
            static_cast<uint32_t>(floats[p + 6])
        };
    }
    std::vector<uint32_t> indices(static_cast<size_t>(indexCount));
    for (size_t i = 0; i < indices.size(); ++i) {
        if (rawIndices[i] < 0) return JNI_FALSE;
        indices[i] = static_cast<uint32_t>(rawIndices[i]);
    }
    return gVulkanBackend.setMesh(vertices, indices) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kingbrezz_slime_bedrock_VulkanBackendNative_nativeRender(JNIEnv*, jclass) {
    return gVulkanBackend.render() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kingbrezz_slime_bedrock_VulkanBackendNative_nativeDetachSurface(JNIEnv*, jclass) {
    gVulkanBackend.detachSurface();
}

extern "C" JNIEXPORT void JNICALL
Java_com_kingbrezz_slime_bedrock_VulkanBackendNative_nativeShutdown(JNIEnv*, jclass) {
    gVulkanBackend.shutdown();
}
