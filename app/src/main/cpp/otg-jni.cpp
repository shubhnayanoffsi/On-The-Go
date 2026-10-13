// Real JNI bridge: Kotlin ShortArray (16kHz s16 mono) -> whisper.cpp -> String.
// Lifecycle per utterance: init model, run full, free. Keeps 6GB RAM free.
#include <jni.h>
#include <string>
#include <vector>
#include "whisper.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_onthego_stt_WhisperEngine_nativeTranscribe(JNIEnv* env, jobject, jstring jModelPath, jshortArray jPcm, jstring jLang) {
    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    if (!modelPath) return env->NewStringUTF("");
    const char* lang = jLang ? env->GetStringUTFChars(jLang, nullptr) : nullptr;
    std::string langStr = (lang && *lang) ? lang : "en";
    if (lang) env->ReleaseStringUTFChars(jLang, lang);

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context* ctx = whisper_init_from_file_with_params(modelPath, cparams);
    env->ReleaseStringUTFChars(jModelPath, modelPath);
    if (!ctx) return env->NewStringUTF("");

    jsize n = env->GetArrayLength(jPcm);
    jshort* pcm16 = env->GetShortArrayElements(jPcm, nullptr);
    std::vector<float> pcmf32;
    pcmf32.reserve((size_t)n);
    for (jsize i = 0; i < n; i++) pcmf32.push_back((float)pcm16[i] / 32768.0f);
    env->ReleaseShortArrayElements(jPcm, pcm16, JNI_ABORT);

    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_realtime   = false;
    wparams.print_progress   = false;
    wparams.print_timestamps = false;
    wparams.translate        = false;
    wparams.language         = langStr.c_str();
    wparams.n_threads        = 4; // Snapdragon 7s Gen 2: 4 threads is the sweet spot

    std::string out;
    if (whisper_full(ctx, wparams, pcmf32.data(), (int)pcmf32.size()) == 0) {
        const int nSeg = whisper_full_n_segments(ctx);
        for (int i = 0; i < nSeg; i++) {
            const char* t = whisper_full_get_segment_text(ctx, i);
            if (t) { out += t; out += " "; }
        }
    }
    whisper_free(ctx);

    // trim trailing space
    while (!out.empty() && out.back() == ' ') out.pop_back();
    return env->NewStringUTF(out.c_str());
}
