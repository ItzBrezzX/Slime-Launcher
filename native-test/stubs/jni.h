// Minimal functional fake of the JNI surface used by native host tests.
#pragma once
#include <cstdint>
#include <string>
#include <vector>
typedef unsigned char jboolean; typedef int jint; typedef float jfloat; typedef double jdouble; typedef int jsize; typedef long long jlong;
struct _jobject {}; typedef _jobject* jobject; typedef jobject jclass; typedef jobject jstring; typedef jobject jobjectArray; typedef jobject jlongArray; typedef jobject jfloatArray; typedef jobject jintArray;
#define JNI_TRUE 1
#define JNI_FALSE 0
#define JNIEXPORT __attribute__((visibility("default")))
#define JNICALL
struct FakeStr:_jobject{std::string s;}; struct FakeArr:_jobject{std::vector<jobject> v;}; struct FakeLongArr:_jobject{std::vector<jlong> v;}; struct FakeFloatArr:_jobject{std::vector<jfloat> v;}; struct FakeIntArr:_jobject{std::vector<jint> v;};
struct JNIEnv {
 const char* GetStringUTFChars(jstring s,jboolean*){return static_cast<FakeStr*>(s)->s.c_str();}
 void ReleaseStringUTFChars(jstring,const char*){}
 jstring NewStringUTF(const char* c){auto*f=new FakeStr;f->s=c;return f;}
 jclass FindClass(const char*){static _jobject c;return &c;}
 jobjectArray NewObjectArray(jsize n,jclass,jobject){auto*a=new FakeArr;a->v.resize(n);return a;}
 void SetObjectArrayElement(jobjectArray a,jsize i,jobject o){static_cast<FakeArr*>(a)->v[i]=o;}
 jlongArray NewLongArray(jsize n){auto*a=new FakeLongArr;a->v.resize(n);return a;}
 void SetLongArrayRegion(jlongArray a,jsize s,jsize n,const jlong*v){auto*t=static_cast<FakeLongArr*>(a);for(jsize i=0;i<n;++i)t->v[static_cast<size_t>(s+i)]=v[i];}
 jfloatArray NewFloatArray(jsize n){auto*a=new FakeFloatArr;a->v.resize(n);return a;}
 void SetFloatArrayRegion(jfloatArray a,jsize s,jsize n,const jfloat*v){auto*t=static_cast<FakeFloatArr*>(a);for(jsize i=0;i<n;++i)t->v[static_cast<size_t>(s+i)]=v[i];}
 jintArray NewIntArray(jsize n){auto*a=new FakeIntArr;a->v.resize(n);return a;}
 void SetIntArrayRegion(jintArray a,jsize s,jsize n,const jint*v){auto*t=static_cast<FakeIntArr*>(a);for(jsize i=0;i<n;++i)t->v[static_cast<size_t>(s+i)]=v[i];}
 void DeleteLocalRef(jobject){}
};
