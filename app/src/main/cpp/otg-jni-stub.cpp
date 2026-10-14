// Stub until whisper.cpp submodule is fetched. Safe defaults, never crashes.
#include <jni.h>
extern "C" JNIEXPORT jboolean JNICALL
Java_com_onthego_stt_WhisperEngine_nativeInit(JNIEnv*, jobject, jstring, jint) {
    return JNI_FALSE;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_onthego_stt_WhisperEngine_nativeTranscribeChunk(JNIEnv* env, jobject, jshortArray, jstring) {
    return env->NewStringUTF("");
}
extern "C" JNIEXPORT void JNICALL
Java_com_onthego_stt_WhisperEngine_nativeFree(JNIEnv*, jobject) {
}
