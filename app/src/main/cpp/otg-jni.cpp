// Resident whisper.cpp context: init once, transcribe many chunks, free on demand.
// Single background-thread use; a mutex guards against races.
#include <jni.h>
#include <mutex>
#include <string>
#include <vector>
#include "whisper.h"

namespace {
std::mutex g_mu;
struct whisper_context* g_ctx = nullptr;
std::string g_model;
int g_threads = 4;
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onthego_stt_WhisperEngine_nativeInit(JNIEnv* env, jobject, jstring jModelPath, jint jThreads) {
    const char* p = jModelPath ? env->GetStringUTFChars(jModelPath, nullptr) : nullptr;
    if (!p) return JNI_FALSE;
    std::string path(p);
    env->ReleaseStringUTFChars(jModelPath, p);
    if (path.empty()) return JNI_FALSE;
    const int threads = jThreads > 0 ? (jThreads > 8 ? 8 : (int)jThreads) : 4;

    std::lock_guard<std::mutex> lock(g_mu);
    if (g_ctx && g_model == path && g_threads == threads) return JNI_TRUE;
    if (g_ctx) { whisper_free(g_ctx); g_ctx = nullptr; }

    struct whisper_context_params cparams = whisper_context_default_params();
    g_ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    if (!g_ctx) return JNI_FALSE;
    g_model = path;
    g_threads = threads;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_onthego_stt_WhisperEngine_nativeTranscribeChunk(JNIEnv* env, jobject, jshortArray jPcm, jstring jLang) {
    const char* lang = jLang ? env->GetStringUTFChars(jLang, nullptr) : nullptr;
    std::string langStr = (lang && *lang) ? lang : "auto";
    if (lang) env->ReleaseStringUTFChars(jLang, lang);

    std::lock_guard<std::mutex> lock(g_mu);
    if (!g_ctx || !jPcm) return env->NewStringUTF("");

    jsize n = env->GetArrayLength(jPcm);
    if (n <= 0) return env->NewStringUTF("");
    jshort* pcm16 = env->GetShortArrayElements(jPcm, nullptr);
    if (!pcm16) return env->NewStringUTF("");
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
    wparams.n_threads        = g_threads;

    std::string out;
    if (whisper_full(g_ctx, wparams, pcmf32.data(), (int)pcmf32.size()) == 0) {
        const int nSeg = whisper_full_n_segments(g_ctx);
        for (int i = 0; i < nSeg; i++) {
            const char* t = whisper_full_get_segment_text(g_ctx, i);
            if (t) { out += t; out += " "; }
        }
    }
    while (!out.empty() && out.back() == ' ') out.pop_back();
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_onthego_stt_WhisperEngine_nativeFree(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mu);
    if (g_ctx) { whisper_free(g_ctx); g_ctx = nullptr; }
    g_model.clear();
}
