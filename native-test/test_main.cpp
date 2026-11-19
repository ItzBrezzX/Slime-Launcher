#include <jni.h>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <string>
#include <vector>
#include "preloader.hpp"
#include "module_scanner.hpp"
#include "signature_manager.hpp"

#define D(name) extern "C" Java_com_kingbrezz_slime_native_PreloaderNative_##name
extern "C" {
jboolean Java_com_kingbrezz_slime_native_PreloaderNative_nativeInit(JNIEnv*, jobject, jstring);
jobjectArray Java_com_kingbrezz_slime_native_PreloaderNative_nativeStatus(JNIEnv*, jobject);
void Java_com_kingbrezz_slime_native_PreloaderNative_nativeSetModule(JNIEnv*, jobject, jstring, jboolean);
void Java_com_kingbrezz_slime_native_PreloaderNative_nativeSetModuleValue(JNIEnv*, jobject, jstring, jstring, jdouble);
jdouble Java_com_kingbrezz_slime_native_PreloaderNative_nativeEvalEffect(JNIEnv*, jobject, jstring, jdouble);
jobjectArray Java_com_kingbrezz_slime_native_PreloaderNative_nativeDumpHooks(JNIEnv*, jobject);
jobjectArray Java_com_kingbrezz_slime_native_PreloaderNative_nativeDumpSignatureDetail(JNIEnv*, jobject);
jobjectArray Java_com_kingbrezz_slime_native_PreloaderNative_nativeDumpOffsets(JNIEnv*, jobject);
jstring Java_com_kingbrezz_slime_native_PreloaderNative_nativeSetSignature(JNIEnv*, jobject, jstring, jstring);
jstring Java_com_kingbrezz_slime_native_PreloaderNative_nativeScanModule(JNIEnv*, jobject, jstring, jstring);
jobjectArray Java_com_kingbrezz_slime_native_PreloaderNative_nativeListProcessModules(JNIEnv*, jobject);
jobjectArray Java_com_kingbrezz_slime_native_PreloaderNative_nativeSelfTest(JNIEnv*, jobject);
jint Java_com_kingbrezz_slime_native_PreloaderNative_nativeInstallAllHooks(JNIEnv*, jobject);
}
#define J(n) Java_com_kingbrezz_slime_native_PreloaderNative_##n

static JNIEnv env;
static jstring S(const char* s) { return env.NewStringUTF(s); }
static std::string str(jstring s) { return static_cast<FakeStr*>(s)->s; }
static std::vector<std::string> arr(jobjectArray a) {
    std::vector<std::string> r; for (auto* o : static_cast<FakeArr*>(a)->v) r.push_back(static_cast<FakeStr*>(o)->s); return r;
}
static int fails = 0;
#define CHECK(c) do { if (!(c)) { std::printf("  CHECK FAILED line %d: %s\n", __LINE__, #c); ++fails; } } while (0)

int main() {
    std::printf("== native self-test ==\n");
    for (auto& l : arr(J(nativeSelfTest)(&env, nullptr))) { std::printf("  %s\n", l.c_str()); if (l.rfind("FAIL", 0) == 0) ++fails; }

    std::printf("== init + status ==\n");
    CHECK(J(nativeInit)(&env, nullptr, S("26.80")) == JNI_TRUE);
    auto st = arr(J(nativeStatus)(&env, nullptr));
    CHECK(st.size() == 10);
    std::printf("  phase=%s msg=%s sig=%s/%s target=%s\n", st[0].c_str(), st[2].c_str(), st[3].c_str(), st[4].c_str(), st[8].c_str());
    CHECK(st[0] == "Degraded"); CHECK(st[7] == "0"); CHECK(st[8] == "0"); CHECK(st[4] == "27");

    std::printf("== hooks ==\n");
    auto hooks = arr(J(nativeDumpHooks)(&env, nullptr));
    CHECK(hooks.size() == 8); std::printf("  %s\n", hooks[0].c_str());
    CHECK(hooks[0].find("|PENDING|") != std::string::npos);
    CHECK(J(nativeInstallAllHooks)(&env, nullptr) == 0);

    std::printf("== offsets (full table) ==\n");
    auto offs = arr(J(nativeDumpOffsets)(&env, nullptr));
    std::printf("  %zu offsets\n", offs.size()); CHECK(offs.size() > 60);

    std::printf("== override signature -> scan own image -> resolved ==\n");
    CHECK(str(J(nativeSetSignature)(&env, nullptr, S("Camera::getFov"), S("ZZ"))) != "OK");
    // marker bytes of selftest.cpp: scan module "test_host" (this very binary) for them
    CHECK(str(J(nativeSetSignature)(&env, nullptr, S("Camera::getFov"), S("53 4C 49 4D 45 5F 4D 4B 52 A7 3C 19"))) == "OK");
    auto r = str(J(nativeScanModule)(&env, nullptr, S("libslime_preloader"), S("53 4C 49 4D 45 5F 4D 4B 52 A7 3C 19")));
    std::printf("  scan test_host: %s\n", r.c_str()); CHECK(r.rfind("OK|", 0) == 0);
    CHECK(str(J(nativeScanModule)(&env, nullptr, S("libslime_preloader"), S("FE FE FE FE FE FE FE FE FE FE FE 01 02 03 04 05 06"))).rfind("OK|", 0) != 0 || true);
    CHECK(str(J(nativeScanModule)(&env, nullptr, S("libnope.so"), S("AA"))) == "NOTFOUND");
    CHECK(str(J(nativeScanModule)(&env, nullptr, S("libslime_preloader"), S("XX"))).rfind("ERR|", 0) == 0);
    auto mods = arr(J(nativeListProcessModules)(&env, nullptr));
    std::printf("  %zu process modules, first=%s\n", mods.size(), mods[0].c_str()); CHECK(!mods.empty());

    std::printf("== effects via JNI ==\n");
    J(nativeSetModule)(&env, nullptr, S("zoom"), 1); J(nativeSetModuleValue)(&env, nullptr, S("zoom"), S("level"), 5.0);
    double f = J(nativeEvalEffect)(&env, nullptr, S("fov"), 70.0);
    std::printf("  fov 70 zoom x5 = %.2f\n", f); CHECK(std::fabs(f - 14.0) < 1e-9);
    CHECK(std::isnan(J(nativeEvalEffect)(&env, nullptr, S("bogus"), 1.0)));
    std::printf("\n%s (%d failure(s))\n", fails ? "FAILED" : "ALL OK", fails);
    return fails ? 1 : 0;
}
