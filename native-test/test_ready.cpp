#include <cstdlib>
#include <dlfcn.h>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include "jni.h"
extern "C" {
jboolean Java_com_kingbrezz_slime_native_PreloaderNative_nativeInit(JNIEnv*, jobject, jstring);
jobjectArray Java_com_kingbrezz_slime_native_PreloaderNative_nativeStatus(JNIEnv*, jobject);
jobjectArray Java_com_kingbrezz_slime_native_PreloaderNative_nativeDumpHooks(JNIEnv*, jobject);
jstring Java_com_kingbrezz_slime_native_PreloaderNative_nativeSetSignature(JNIEnv*, jobject, jstring, jstring);
jint Java_com_kingbrezz_slime_native_PreloaderNative_nativeInstallAllHooks(JNIEnv*, jobject);
}
#define J(n) Java_com_kingbrezz_slime_native_PreloaderNative_##n
static JNIEnv env;
static std::vector<std::string> arr(jobjectArray a){std::vector<std::string> r;for(auto*o:static_cast<FakeArr*>(a)->v)r.push_back(static_cast<FakeStr*>(o)->s);return r;}
int main(){
  void* h = dlopen(std::getenv("SLIME_FAKE_MC") ? std::getenv("SLIME_FAKE_MC") : "./libminecraftpe.so", RTLD_NOW); if(!h){puts("dlopen fail");return 2;}
  // override survives init: set BEFORE init, table empty at that time (like app start)
  auto r = static_cast<FakeStr*>(J(nativeSetSignature)(&env,nullptr,env.NewStringUTF("Camera::getFov"),env.NewStringUTF("F4 0F 1E F8 ?? ?? ?? 91 C0 03 5F D6")))->s;
  printf("set override: %s\n", r.c_str());
  J(nativeInit)(&env,nullptr,env.NewStringUTF("26.80"));
  auto st = arr(J(nativeStatus)(&env,nullptr));
  printf("phase=%s sig=%s/%s target=%s base=%s msg=%s\n", st[0].c_str(), st[3].c_str(), st[4].c_str(), st[8].c_str(), st[9].c_str(), st[2].c_str());
  bool ok = st[0]=="Ready" && st[3]=="1" && st[7]=="1" && st[8]=="1";
  for (auto& l : arr(J(nativeDumpHooks)(&env,nullptr))) if (l.find("CameraFov")==0) { printf("%s\n", l.c_str()); ok = ok && l.find("|IDLE|")!=std::string::npos; }
  int n = J(nativeInstallAllHooks)(&env,nullptr);
  for (auto& l : arr(J(nativeDumpHooks)(&env,nullptr))) if (l.find("CameraFov")==0) { printf("after install: %s\n", l.c_str()); ok = ok && l.find("|FAILED|")!=std::string::npos; }
  printf("installAll active=%d (expected 0: no trampoline backend)\n", n); ok = ok && n==0;
  puts(ok ? "READY-PATH OK" : "READY-PATH FAILED"); return ok?0:1;
}
