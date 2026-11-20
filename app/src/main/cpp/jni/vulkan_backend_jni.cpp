#include <jni.h>
#include <cstdio>
#include "vulkan_backend.hpp"

namespace {
slime::bedrock::VulkanBackend gVulkanBackend;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_kingbrezz_slime_bedrock_VulkanBackendNative_nativeInitialize(JNIEnv* env, jclass) {
    const auto info = gVulkanBackend.initialize();
    char vendor[16]{};
    char type[16]{};
    char memory[32]{};
    char queueFamily[16]{};
    std::snprintf(vendor, sizeof(vendor), "%u", info.vendorId);
    std::snprintf(type, sizeof(type), "%u", info.deviceType);
    std::snprintf(memory, sizeof(memory), "%llu",
                  static_cast<unsigned long long>(info.deviceLocalMemoryBytes));
    std::snprintf(queueFamily, sizeof(queueFamily), "%u", info.graphicsQueueFamily);
    const char* values[] = {
        info.available ? "true" : "false",
        info.status.c_str(),
        info.deviceName.c_str(),
        info.apiVersion.c_str(),
        info.driverVersion.c_str(),
        vendor,
        type,
        memory,
        queueFamily
    };
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(9, stringClass, nullptr);
    if (result == nullptr) return nullptr;
    for (jsize i = 0; i < 9; ++i) {
        jstring value = env->NewStringUTF(values[i]);
        if (value == nullptr) return result;
        env->SetObjectArrayElement(result, i, value);
        env->DeleteLocalRef(value);
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kingbrezz_slime_bedrock_VulkanBackendNative_nativeShutdown(JNIEnv*, jclass) {
    gVulkanBackend.shutdown();
}
