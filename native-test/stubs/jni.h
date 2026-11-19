// Minimal functional fake of the JNI surface used by jni_bridge.cpp (host testing only).
#pragma once
#include <cstdint>
#include <string>
#include <vector>
typedef unsigned char jboolean; typedef int jint; typedef double jdouble; typedef int jsize;
struct _jobject {}; typedef _jobject* jobject; typedef jobject jclass; typedef jobject jstring; typedef jobject jobjectArray;
#define JNI_TRUE 1
#define JNI_FALSE 0
#define JNIEXPORT __attribute__((visibility("default")))
#define JNICALL
struct FakeStr : _jobject { std::string s; };
struct FakeArr : _jobject { std::vector<jobject> v; };
struct JNIEnv {
    const char* GetStringUTFChars(jstring s, jboolean*) { return static_cast<FakeStr*>(s)->s.c_str(); }
    void ReleaseStringUTFChars(jstring, const char*) {}
    jstring NewStringUTF(const char* c) { auto* f = new FakeStr; f->s = c; return f; }
    jclass FindClass(const char*) { static _jobject c; return &c; }
    jobjectArray NewObjectArray(jsize n, jclass, jobject) { auto* a = new FakeArr; a->v.resize(n); return a; }
    void SetObjectArrayElement(jobjectArray a, jsize i, jobject o) { static_cast<FakeArr*>(a)->v[i] = o; }
    void DeleteLocalRef(jobject) {}
};
