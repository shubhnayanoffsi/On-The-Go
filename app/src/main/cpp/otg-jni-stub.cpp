// Stub until whisper.cpp submodule is fetched. Returns empty string.
#include <jni.h>
extern "C" JNIEXPORT jstring JNICALL
Java_com_onthego_stt_WhisperEngine_nativeTranscribe(JNIEnv* env, jobject, jstring, jshortArray) {
    return env->NewStringUTF("");
}
