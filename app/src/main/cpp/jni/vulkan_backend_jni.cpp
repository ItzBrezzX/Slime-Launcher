#include <jni.h>
#include "vulkan_backend.hpp"

namespace {
slime::bedrock::VulkanBackend gVulkanBackend;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_kingbrezz_slime_bedrock_VulkanBackendNative_nativeInitialize(JNIEnv* env, jclass) {
    const auto info = gVulkanBackend.initialize();
    const char* values[] = {
        info.available ? "true" : "false",
        info.status.c_str(),
        info.deviceName.c_str(),
        info.apiVersion.c_str(),
        info.driverVersion.c_str(),
        nullptr,
        nullptr,
        nullptr,
    };
    char vendor[16]{};
    char type[16]{};
    char memory[32]{};
    std::snprintf(vendor, sizeof(vendor), "%u", info.vendorId);
    std::snprintf(type, sizeof(type), "%u", info.deviceType);
    std::snprintf(memory, sizeof(memory), "%llu",
                  static_cast<unsigned long long>(info.deviceLocalMemoryBytes));
    const char* resultValues[] = {
        values[0], values[1], values[2], values[3], values[4],
        vendor, type, memory
    };
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(8, stringClass, nullptr);
    if (result == nullptr) return nullptr;
    for (jsize i = 0; i < 8; ++i) {
        jstring value = env->NewStringUTF(resultValues[i]);
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
