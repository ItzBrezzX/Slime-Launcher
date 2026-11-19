#include <jni.h>
#include <cmath>
#include <cstdio>
#include <string>
#include <vector>
#include "preloader.hpp"
#include "module_bridge.hpp"
#include "module_scanner.hpp"
#include "hook_registry.hpp"
#include "signature_manager.hpp"
#include "offset_tables.hpp"
#include "effects.hpp"
#include "slime_log.hpp"

namespace {

std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

jstring jnew(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

jobjectArray toArray(JNIEnv* env, const std::vector<std::string>& rows) {
    jclass strCls = env->FindClass("java/lang/String");
    if (!strCls) return nullptr;
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(rows.size()), strCls, nullptr);
    if (arr) {
        for (jsize i = 0; i < static_cast<jsize>(rows.size()); ++i) {
            jstring s = jnew(env, rows[static_cast<size_t>(i)]);
            env->SetObjectArrayElement(arr, i, s);
            if (s) env->DeleteLocalRef(s);
        }
    }
    env->DeleteLocalRef(strCls);
    return arr;
}

std::string hex(uintptr_t v) {
    char b[32];
    std::snprintf(b, sizeof(b), "0x%llx", static_cast<unsigned long long>(v));
    return b;
}

const char* phaseName(slime::PreloaderPhase p) {
    switch (p) {
        case slime::PreloaderPhase::InitNative: return "InitNative";
        case slime::PreloaderPhase::LoadOffsets: return "LoadOffsets";
        case slime::PreloaderPhase::ScanSignatures: return "ScanSignatures";
        case slime::PreloaderPhase::ResolveHooks: return "ResolveHooks";
        case slime::PreloaderPhase::Ready: return "Ready";
        case slime::PreloaderPhase::Degraded: return "Degraded";
        case slime::PreloaderPhase::Error: return "Error";
        default: return "Idle";
    }
}

#define SLIME_JNI(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_kingbrezz_slime_native_PreloaderNative_##name

} // namespace

// ---------------------------------------------------------------- lifecycle

SLIME_JNI(jboolean, nativeInit)(JNIEnv* env, jobject, jstring versionId) {
    return slime::Preloader::instance().initialize(jstr(env, versionId)) ? JNI_TRUE : JNI_FALSE;
}

SLIME_JNI(void, nativeShutdown)(JNIEnv*, jobject) {
    slime::Preloader::instance().shutdown();
}

/** [phase, version, message, sigResolved, sigTotal, hooksActive, hooksPending, canInject, targetFound, targetBase] */
SLIME_JNI(jobjectArray, nativeStatus)(JNIEnv* env, jobject) {
    const auto st = slime::Preloader::instance().status();
    return toArray(env, {
        phaseName(st.phase),
        st.versionId,
        st.message,
        std::to_string(st.sigResolved),
        std::to_string(st.sigTotal),
        std::to_string(st.hooksActive),
        std::to_string(st.hooksPending),
        st.canInject ? "1" : "0",
        st.targetFound ? "1" : "0",
        hex(st.targetBase),
    });
}

SLIME_JNI(jstring, nativeLibraryVersion)(JNIEnv* env, jobject) {
    return jnew(env, "slime-preloader-1.5.0");
}

// ------------------------------------------------------------------ modules

SLIME_JNI(void, nativeSetModule)(JNIEnv* env, jobject, jstring id, jboolean enabled) {
    slime::ModuleBridge::instance().setEnabled(jstr(env, id), enabled == JNI_TRUE);
}

SLIME_JNI(jboolean, nativeIsModuleEnabled)(JNIEnv* env, jobject, jstring id) {
    return slime::ModuleBridge::instance().isEnabled(jstr(env, id)) ? JNI_TRUE : JNI_FALSE;
}

SLIME_JNI(jint, nativeEnabledModuleCount)(JNIEnv*, jobject) {
    return slime::ModuleBridge::instance().enabledCount();
}

SLIME_JNI(void, nativeSetModuleValue)(JNIEnv* env, jobject, jstring id, jstring key, jdouble value) {
    slime::ModuleBridge::instance().setValue(jstr(env, id), jstr(env, key), value);
}

SLIME_JNI(jdouble, nativeGetModuleValue)(JNIEnv* env, jobject, jstring id, jstring key, jdouble def) {
    return slime::ModuleBridge::instance().getValue(jstr(env, id), jstr(env, key), def);
}

/** "id|0/1" */
SLIME_JNI(jobjectArray, nativeDumpModules)(JNIEnv* env, jobject) {
    std::vector<std::string> rows;
    for (const auto& kv : slime::ModuleBridge::instance().flags()) {
        rows.push_back(kv.first + "|" + (kv.second ? "1" : "0"));
    }
    return toArray(env, rows);
}

/** "effect preview": returns NaN for unknown kind. */
SLIME_JNI(jdouble, nativeEvalEffect)(JNIEnv* env, jobject, jstring kind, jdouble base) {
    bool known = false;
    const double v = slime::effects::evaluate(jstr(env, kind), base, &known);
    return known ? v : std::nan("");
}

// -------------------------------------------------------------------- hooks

SLIME_JNI(jboolean, nativeTryInstallHook)(JNIEnv* env, jobject, jstring name) {
    return slime::HookRegistry::instance().tryInstall(jstr(env, name)) ? JNI_TRUE : JNI_FALSE;
}

SLIME_JNI(jint, nativeInstallAllHooks)(JNIEnv*, jobject) {
    return slime::HookRegistry::instance().installAll();
}

/** "name|sigName|STATUS|0xtarget|reason" */
SLIME_JNI(jobjectArray, nativeDumpHooks)(JNIEnv* env, jobject) {
    std::vector<std::string> rows;
    for (const auto& h : slime::HookRegistry::instance().all()) {
        rows.push_back(h.name + "|" + h.sigName + "|" + slime::hookStatusName(h.status) + "|" +
                       hex(h.target) + "|" + h.reason);
    }
    return toArray(env, rows);
}

// ------------------------------------------------- signatures + offsets

/** Complete offset table: "name=value" */
SLIME_JNI(jobjectArray, nativeDumpOffsets)(JNIEnv* env, jobject) {
    std::vector<std::string> rows;
    for (const auto& kv : slime::listOffsets()) rows.push_back(kv.first + "=" + std::to_string(kv.second));
    return toArray(env, rows);
}

SLIME_JNI(jobjectArray, nativeDumpSignatures)(JNIEnv* env, jobject) {
    std::vector<std::string> rows;
    for (const auto& kv : slime::listSignatureStatus()) rows.push_back(kv.first + (kv.second ? "=RESOLVED" : "=PENDING"));
    return toArray(env, rows);
}

/** "name|pattern|RESOLVED/PENDING|0xaddr" */
SLIME_JNI(jobjectArray, nativeDumpSignatureDetail)(JNIEnv* env, jobject) {
    std::vector<std::string> rows;
    for (const auto& e : slime::SignatureManager::instance().signatures()) {
        rows.push_back(e.name + "|" + e.pattern + "|" + (e.resolved ? "RESOLVED" : "PENDING") + "|" + hex(e.address));
    }
    return toArray(env, rows);
}

/** Returns "OK" or an error message. Empty pattern removes the override. */
SLIME_JNI(jstring, nativeSetSignature)(JNIEnv* env, jobject, jstring name, jstring pattern) {
    std::string err;
    const bool ok = slime::SignatureManager::instance().setOverrideSignature(jstr(env, name), jstr(env, pattern), &err);
    return jnew(env, ok ? "OK" : err);
}

SLIME_JNI(void, nativeSetOffset)(JNIEnv* env, jobject, jstring name, jint value) {
    slime::SignatureManager::instance().setOverrideOffset(jstr(env, name), value);
}

SLIME_JNI(void, nativeClearOverrides)(JNIEnv*, jobject) {
    slime::SignatureManager::instance().clearOverrides();
}

// ------------------------------------------------------------ process tools

/** "name|0xbase|size" for every module mapped in this process. */
SLIME_JNI(jobjectArray, nativeListProcessModules)(JNIEnv* env, jobject) {
    std::vector<std::string> rows;
    for (const auto& m : slime::listLoadedModules()) {
        rows.push_back(m.name + "|" + hex(m.base) + "|" + std::to_string(m.size));
    }
    return toArray(env, rows);
}

/**
 * Pattern tester. Returns:
 *   "OK|0xabs|0xrel|0xbase"   match
 *   "NOMATCH|0xbase"          module found, pattern not
 *   "NOTFOUND"                module not mapped in this process
 *   "ERR|message"             bad pattern
 */
SLIME_JNI(jstring, nativeScanModule)(JNIEnv* env, jobject, jstring moduleName, jstring pattern) {
    std::vector<int> bytes;
    std::string err;
    if (!slime::parsePattern(jstr(env, pattern), &bytes, &err)) return jnew(env, "ERR|" + err);

    slime::LoadedModule mod;
    if (!slime::findLoadedModule(jstr(env, moduleName), &mod)) return jnew(env, "NOTFOUND");

    const uintptr_t hit = slime::scanRanges(mod.readable, bytes);
    if (hit == 0) return jnew(env, "NOMATCH|" + hex(mod.base));
    return jnew(env, "OK|" + hex(hit) + "|" + hex(hit - mod.base) + "|" + hex(mod.base));
}

SLIME_JNI(jobjectArray, nativeSelfTest)(JNIEnv* env, jobject) {
    return toArray(env, slime::runSelfTest());
}
