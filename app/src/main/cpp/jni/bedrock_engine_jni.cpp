#include <jni.h>
#include <cstdint>
#include <vector>
#include "bedrock_engine.hpp"
namespace { slime::bedrock::Engine gEngine; }
extern "C" JNIEXPORT void JNICALL Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeCreateWorld(JNIEnv*,jclass,jlong seed){gEngine.createWorld(static_cast<int64_t>(seed));}
extern "C" JNIEXPORT jboolean JNICALL Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeGenerateChunk(JNIEnv*,jclass,jint x,jint z){return gEngine.generateChunk(x,z)?JNI_TRUE:JNI_FALSE;}
extern "C" JNIEXPORT jint JNICALL Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeGetBlock(JNIEnv*,jclass,jint x,jint y,jint z){return static_cast<jint>(gEngine.blockAt(x,y,z));}
extern "C" JNIEXPORT jlongArray JNICALL Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeGetChunkStats(JNIEnv* env,jclass,jint x,jint z){
 const auto s=gEngine.chunkStats(x,z); const jlong v[5]={static_cast<jlong>(s.solidBlocks),static_cast<jlong>(s.waterBlocks),static_cast<jlong>(s.visibleFaces),static_cast<jlong>(s.minHeight),static_cast<jlong>(s.maxHeight)};
 jlongArray a=env->NewLongArray(5);if(a)env->SetLongArrayRegion(a,0,5,v);return a;
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeGetChunkMeshVertices(JNIEnv* env,jclass,jint x,jint z){
 const auto m=gEngine.buildChunkMesh(x,z);std::vector<jfloat> v;v.reserve(m.vertices.size()*7);
 for(const auto& p:m.vertices)v.insert(v.end(),{p.x,p.y,p.z,p.nx,p.ny,p.nz,static_cast<jfloat>(p.blockId)});
 jfloatArray a=env->NewFloatArray(static_cast<jsize>(v.size()));if(a&&!v.empty())env->SetFloatArrayRegion(a,0,static_cast<jsize>(v.size()),v.data());return a;
}
extern "C" JNIEXPORT jintArray JNICALL Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeGetChunkMeshIndices(JNIEnv* env,jclass,jint x,jint z){
 const auto m=gEngine.buildChunkMesh(x,z);std::vector<jint> v(m.indices.begin(),m.indices.end());
 jintArray a=env->NewIntArray(static_cast<jsize>(v.size()));if(a&&!v.empty())env->SetIntArrayRegion(a,0,static_cast<jsize>(v.size()),v.data());return a;
}
extern "C" JNIEXPORT jlong JNICALL Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeChunkStorageBytes(JNIEnv*,jclass,jint x,jint z){return static_cast<jlong>(gEngine.chunkStorageBytes(x,z));}
extern "C" JNIEXPORT jlong JNICALL Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeTotalStorageBytes(JNIEnv*,jclass){return static_cast<jlong>(gEngine.totalStorageBytes());}
extern "C" JNIEXPORT jint JNICALL Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeLoadedChunkCount(JNIEnv*,jclass){return static_cast<jint>(gEngine.loadedChunkCount());}
extern "C" JNIEXPORT void JNICALL Java_com_kingbrezz_slime_bedrock_BedrockEngineNative_nativeReset(JNIEnv*,jclass){gEngine.reset();}
