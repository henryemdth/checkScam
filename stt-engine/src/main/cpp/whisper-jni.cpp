#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <string>
#include <mutex>
#include <vector>

#include "whisper.h"

#define LOG_TAG "whisper-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Single whisper context, loaded once at initialization and reused for every
// chunk. This satisfies the constraint that the model is loaded only once.
static whisper_context* g_context = nullptr;
static std::mutex g_mutex;

static void trim_in_place(std::string& s) {
    size_t start = s.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) {
        s.clear();
        return;
    }
    size_t end = s.find_last_not_of(" \t\r\n");
    s = s.substr(start, end - start + 1);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_checkscam_stt_WhisperEngineImpl_nativeInitialize(JNIEnv* env, jobject /*thiz*/,
                                                           jstring modelPath) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_context != nullptr) {
        LOGE("nativeInitialize called when context already present");
        return 0;
    }

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        return 0;
    }

    whisper_context_params cparams = whisper_context_default_params();
    g_context = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (g_context == nullptr) {
        LOGE("Failed to load whisper model");
        return 0;
    }

    LOGI("whisper model loaded successfully");
    return reinterpret_cast<jlong>(g_context);
}

JNIEXPORT jstring JNICALL
Java_com_checkscam_stt_WhisperEngineImpl_nativeTranscribe(JNIEnv* env, jobject /*thiz*/,
                                                           jshortArray pcmData) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_context == nullptr) {
        LOGE("nativeTranscribe called before initialization");
        return env->NewStringUTF("");
    }

    const jsize n = env->GetArrayLength(pcmData);
    if (n <= 0) {
        return env->NewStringUTF("");
    }

    std::vector<jshort> tmp(n);
    env->GetShortArrayRegion(pcmData, 0, n, tmp.data());

    // Convert PCM16 short samples to float in range [-1, 1] for whisper.
    std::vector<float> samples(n);
    for (jsize i = 0; i < n; ++i) {
        samples[i] = static_cast<float>(tmp[i]) / 32768.0f;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "es";
    params.no_timestamps = true;
    params.single_segment = false;
    params.n_threads = 4;

    std::string transcript;

    int ret = whisper_full(g_context, params, samples.data(), n);
    if (ret != 0) {
        LOGE("whisper_full failed with code %d", ret);
        return env->NewStringUTF("");
    }

    transcript.reserve(1024);
    const int n_segments = whisper_full_n_segments(g_context);
    for (int i = 0; i < n_segments; ++i) {
        const char* text = whisper_full_get_segment_text(g_context, i);
        if (text != nullptr) {
            transcript.append(text);
        }
    }

    trim_in_place(transcript);

    return env->NewStringUTF(transcript.c_str());
}

JNIEXPORT void JNICALL
Java_com_checkscam_stt_WhisperEngineImpl_nativeReset(JNIEnv* /*env*/, jobject /*thiz*/) {
    // whisper_full is stateless across invocations, so reset is a no-op aside
    // from guarding against use before init.
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context == nullptr) {
        LOGE("nativeReset called before initialization");
    }
}

JNIEXPORT void JNICALL
Java_com_checkscam_stt_WhisperEngineImpl_nativeRelease(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context != nullptr) {
        whisper_free(g_context);
        g_context = nullptr;
        LOGI("whisper context released");
    }
}

}
