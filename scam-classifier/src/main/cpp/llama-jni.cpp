#include <jni.h>
#include <android/log.h>

#include <chrono>
#include <cstdint>
#include <fstream>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "llama-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int32_t N_THREADS = 4;
constexpr int32_t N_GEN_TOKENS_MAX = 512;
constexpr uint32_t DIST_SEED = 42;

// Single llama model + context, loaded once at initialization and reused
// across every classify() call (KV cache cleared per request). Grammar GBNF
// content is loaded once and kept in memory.
llama_model* g_model = nullptr;
llama_context* g_ctx = nullptr;
const llama_vocab* g_vocab = nullptr;
std::string g_grammar;
std::mutex g_mutex;

uint32_t g_n_ctx = 0;

bool read_file(const std::string& path, std::string& out) {
    std::ifstream f(path, std::ios::binary);
    if (!f.is_open()) {
        return false;
    }
    std::ostringstream ss;
    ss << f.rdbuf();
    out = ss.str();
    return true;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_checkscam_classifier_LlamaEngineImpl_nativeInitialize(
    JNIEnv* env, jobject /*thiz*/, jstring modelPath, jstring grammarPath, jint nCtx) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model != nullptr) {
        LOGE("nativeInitialize called when model already loaded");
        return 0;
    }

    const char* model_c = env->GetStringUTFChars(modelPath, nullptr);
    const char* grammar_c = env->GetStringUTFChars(grammarPath, nullptr);
    if (model_c == nullptr || grammar_c == nullptr) {
        if (model_c != nullptr) env->ReleaseStringUTFChars(modelPath, model_c);
        if (grammar_c != nullptr) env->ReleaseStringUTFChars(grammarPath, grammar_c);
        return 0;
    }

    const std::string model_path(model_c);
    const std::string grammar_path(grammar_c);
    env->ReleaseStringUTFChars(modelPath, model_c);
    env->ReleaseStringUTFChars(grammarPath, grammar_c);

    if (nCtx <= 0) {
        LOGE("Invalid n_ctx (%d)", nCtx);
        return 0;
    }

    // Grammar is optional: if the file is missing, fall back to unconstrained
    // output and let the Kotlin parser sanitize it.
    if (!read_file(grammar_path, g_grammar)) {
        LOGE("Failed to read grammar file %s; continuing without grammar", grammar_path.c_str());
        g_grammar.clear();
    }

    llama_backend_init();

    try {
        llama_model_params mparams = llama_model_default_params();
        mparams.n_gpu_layers = 0;

        g_model = llama_model_load_from_file(model_path.c_str(), mparams);
        if (g_model == nullptr) {
            LOGE("Failed to load GGUF model from %s", model_path.c_str());
            g_grammar.clear();
            return 0;
        }

        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx = static_cast<uint32_t>(nCtx);
        cparams.n_threads = N_THREADS;
        cparams.n_threads_batch = N_THREADS;

        g_ctx = llama_init_from_model(g_model, cparams);
        if (g_ctx == nullptr) {
            LOGE("Failed to create llama context (n_ctx=%d)", nCtx);
            llama_model_free(g_model);
            g_model = nullptr;
            g_grammar.clear();
            return 0;
        }
    } catch (const std::exception& e) {
        LOGE("nativeInitialize exception: %s", e.what());
        g_grammar.clear();
        if (g_ctx != nullptr) {
            llama_free(g_ctx);
            g_ctx = nullptr;
        }
        if (g_model != nullptr) {
            llama_model_free(g_model);
            g_model = nullptr;
        }
        return 0;
    } catch (...) {
        LOGE("nativeInitialize unknown exception");
        g_grammar.clear();
        if (g_ctx != nullptr) {
            llama_free(g_ctx);
            g_ctx = nullptr;
        }
        if (g_model != nullptr) {
            llama_model_free(g_model);
            g_model = nullptr;
        }
        return 0;
    }

    g_vocab = llama_model_get_vocab(g_model);
    g_n_ctx = llama_n_ctx(g_ctx);

    LOGI("llama model loaded: %zu bytes, %zu params, n_ctx=%u",
         (size_t)llama_model_size(g_model), (size_t)llama_model_n_params(g_model), g_n_ctx);
    return 1;
}

JNIEXPORT jstring JNICALL
Java_com_checkscam_classifier_LlamaEngineImpl_nativeClassify(JNIEnv* env, jobject /*thiz*/,
                                                              jstring prompt) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model == nullptr || g_ctx == nullptr) {
        LOGE("nativeClassify called before initialization");
        return env->NewStringUTF("");
    }

    const char* prompt_c = env->GetStringUTFChars(prompt, nullptr);
    if (prompt_c == nullptr) {
        return env->NewStringUTF("");
    }
    std::string prompt_str(prompt_c);
    env->ReleaseStringUTFChars(prompt, prompt_c);

    if (prompt_str.empty()) {
        return env->NewStringUTF("");
    }

    try {
        // Tokenize the prompt (two-pass: size query, then fill buffer).
    // NOTE: pass the real byte length, never -1: llama_vocab::tokenize builds
    // std::string(text, text_len) and (size_t)-1 throws std::length_error,
    // which must not escape the JNI boundary.
    const int32_t prompt_len = static_cast<int32_t>(prompt_str.length());
    int32_t n_prompt = llama_tokenize(g_vocab, prompt_str.c_str(), prompt_len, nullptr, 0, false, true);
    if (n_prompt < 0) {
        n_prompt = -n_prompt;
    }
    if (n_prompt <= 0) {
        return env->NewStringUTF("");
    }
    // Leave room for the generated tail inside n_ctx.
    const int32_t available = static_cast<int32_t>(g_n_ctx) - 1;
    if (n_prompt > available) {
        LOGE("Prompt too long (%d tokens, n_ctx=%u); truncating", n_prompt, g_n_ctx);
        n_prompt = available;
    }

    std::vector<llama_token> toks(n_prompt);
    const int32_t n_tok = llama_tokenize(g_vocab, prompt_str.c_str(), prompt_len,
                                         toks.data(), static_cast<int32_t>(toks.size()), false, true);
    if (n_tok <= 0) {
        return env->NewStringUTF("");
    }
    n_prompt = n_tok;

    // Fresh sequence per call: discard previous memory/KV state.
    llama_memory_clear(llama_get_memory(g_ctx), true);

    // Feed the whole prompt in one batch (positions auto-tracked via pos array).
    llama_batch pb = llama_batch_init(n_prompt, 0, 1);
    if (pb.token == nullptr) {
        LOGE("llama_batch_init (prompt) OOM");
        return env->NewStringUTF("");
    }
    pb.n_tokens = n_prompt;  // llama_batch_init leaves this 0; llama_decode rejects empty batches
    for (int32_t i = 0; i < n_prompt; ++i) {
        pb.token[i] = toks[i];
        pb.pos[i] = i;
        pb.n_seq_id[i] = 1;
        pb.seq_id[i][0] = 0;
        pb.logits[i] = (i == n_prompt - 1);
    }
    const auto t_prompt_start = std::chrono::steady_clock::now();
    if (llama_decode(g_ctx, pb) != 0) {
        LOGE("llama_decode (prompt) failed");
        llama_batch_free(pb);
        return env->NewStringUTF("");
    }
    llama_batch_free(pb);

    // Sampler chain: GBNF grammar (optional) + greedy temp + final distribution.
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    if (smpl == nullptr) {
        LOGE("llama_sampler_chain_init failed");
        return env->NewStringUTF("");
    }
    if (!g_grammar.empty()) {
        llama_sampler* gsmpl = llama_sampler_init_grammar(g_vocab, g_grammar.c_str(), "root");
        if (gsmpl != nullptr) {
            llama_sampler_chain_add(smpl, gsmpl);
        } else {
            LOGE("Grammar parse failed; continuing without grammar");
        }
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.0f));  // greedy
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(DIST_SEED));

    std::string output;
    output.reserve(2048);

    llama_batch gen_batch = llama_batch_init(N_GEN_TOKENS_MAX, 0, 1);
    if (gen_batch.token == nullptr) {
        LOGE("llama_batch_init (generation) OOM");
        llama_sampler_free(smpl);
        return env->NewStringUTF("");
    }

    int32_t cur_pos = n_prompt;
    for (int32_t i = 0; i < N_GEN_TOKENS_MAX; ++i) {
        if (cur_pos >= static_cast<int32_t>(g_n_ctx) - 1) {
            LOGI("Reached n_ctx limit while generating");
            break;
        }

        const llama_token tok = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(g_vocab, tok)) {
            break;
        }

        char piece[1024];
        int32_t len = llama_token_to_piece(g_vocab, tok, piece, sizeof(piece), 0, false);
        if (len < 0) {
            len = -len;  // required buffer size larger than sizeof(piece)
        }
        if (len > 0) {
            output.append(piece, static_cast<size_t>(len));
            // TTFT: first decode (prompt) -> first decoded output token.
            if (i == 0) {
                const auto us = std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now() - t_prompt_start).count();
                LOGI("LLAMA_TTFT_MS=%lld", static_cast<long long>(us / 1000));
            }
        }

        gen_batch.n_tokens = 1;
        gen_batch.token[0] = tok;
        gen_batch.pos[0] = cur_pos;
        gen_batch.n_seq_id[0] = 1;
        gen_batch.seq_id[0][0] = 0;
        gen_batch.logits[0] = true;

        if (llama_decode(g_ctx, gen_batch) != 0) {
            LOGE("llama_decode (generation) failed at step %d", i);
            break;
        }
        ++cur_pos;
    }

    llama_batch_free(gen_batch);
    llama_sampler_free(smpl);

    return env->NewStringUTF(output.c_str());
    } catch (const std::exception& e) {
        LOGE("nativeClassify exception: %s", e.what());
        return env->NewStringUTF("");
    } catch (...) {
        LOGE("nativeClassify unknown exception");
        return env->NewStringUTF("");
    }
}

JNIEXPORT void JNICALL
Java_com_checkscam_classifier_LlamaEngineImpl_nativeReset(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx != nullptr) {
        llama_memory_clear(llama_get_memory(g_ctx), true);
    }
}

JNIEXPORT void JNICALL
Java_com_checkscam_classifier_LlamaEngineImpl_nativeRelease(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
    g_grammar.clear();
    llama_backend_free();
    LOGI("llama model + context released");
}

}  // extern "C"