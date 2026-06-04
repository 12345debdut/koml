// JNI bridge between Kotlin LlamaNative and llama.cpp
//
// Verify function names against external/llama.cpp/include/llama.h if
// compilation fails — the llama.cpp API surface changes across releases.

#include <jni.h>
#include <string>
#include <vector>
#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#define TAG "KomlEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(fmt, ...) fprintf(stderr, "[KomlEngine] " fmt "\n", ##__VA_ARGS__)
#define LOGE(fmt, ...) fprintf(stderr, "[KomlEngine][ERR] " fmt "\n", ##__VA_ARGS__)
#endif

static void throw_jni(JNIEnv *env, const char *msg) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls) env->ThrowNew(cls, msg);
}

extern "C" {

// ── lifecycle ───────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_dev_koml_engine_LlamaNative_initBackend(JNIEnv *, jobject) {
    llama_backend_init();
    LOGI("Backend initialized");
}

JNIEXPORT jlong JNICALL
Java_dev_koml_engine_LlamaNative_loadModel(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);

    auto params = llama_model_default_params();
    params.n_gpu_layers = 0;

    llama_model *model = llama_model_load_from_file(path, params);
    env->ReleaseStringUTFChars(jpath, path);

    if (!model) {
        throw_jni(env, "Failed to load model");
        return 0;
    }
    LOGI("Model loaded");
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT void JNICALL
Java_dev_koml_engine_LlamaNative_freeModel(JNIEnv *, jobject, jlong handle) {
    auto *model = reinterpret_cast<llama_model *>(handle);
    if (model) {
        llama_model_free(model);
        LOGI("Model freed");
    }
}

JNIEXPORT jlong JNICALL
Java_dev_koml_engine_LlamaNative_createContext(JNIEnv *env, jobject,
                                               jlong model_handle, jint n_ctx) {
    auto *model = reinterpret_cast<llama_model *>(model_handle);

    auto params = llama_context_default_params();
    params.n_ctx    = n_ctx;
    params.n_threads = 4;

    llama_context *ctx = llama_init_from_model(model, params);
    if (!ctx) {
        throw_jni(env, "Failed to create context");
        return 0;
    }
    LOGI("Context created (n_ctx=%d)", n_ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_dev_koml_engine_LlamaNative_freeContext(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<llama_context *>(handle);
    if (ctx) {
        llama_free(ctx);
        LOGI("Context freed");
    }
}

// ── tokenization ────────────────────────────────────────────────────

JNIEXPORT jintArray JNICALL
Java_dev_koml_engine_LlamaNative_tokenize(JNIEnv *env, jobject,
                                          jlong model_handle, jstring jtext,
                                          jboolean add_bos) {
    auto *model = reinterpret_cast<llama_model *>(model_handle);
    const llama_vocab *vocab = llama_model_get_vocab(model);
    const char *text = env->GetStringUTFChars(jtext, nullptr);
    int text_len = static_cast<int>(strlen(text));

    // First call: get required token count (returned as negative when buffer is too small)
    int n = -llama_tokenize(vocab, text, text_len, nullptr, 0, add_bos, false);
    if (n <= 0) {
        env->ReleaseStringUTFChars(jtext, text);
        throw_jni(env, "Tokenization failed");
        return nullptr;
    }

    std::vector<llama_token> tokens(n);
    n = llama_tokenize(vocab, text, text_len, tokens.data(),
                       static_cast<int>(tokens.size()), add_bos, false);
    env->ReleaseStringUTFChars(jtext, text);

    if (n < 0) {
        throw_jni(env, "Tokenization failed on second pass");
        return nullptr;
    }

    jintArray result = env->NewIntArray(n);
    env->SetIntArrayRegion(result, 0, n, reinterpret_cast<jint *>(tokens.data()));
    return result;
}

// ── decode ──────────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_dev_koml_engine_LlamaNative_decode(JNIEnv *env, jobject,
                                        jlong ctx_handle, jintArray jtokens) {
    auto *ctx = reinterpret_cast<llama_context *>(ctx_handle);

    int n_tokens = env->GetArrayLength(jtokens);
    jint *tokens = env->GetIntArrayElements(jtokens, nullptr);

    llama_batch batch = llama_batch_get_one(
        reinterpret_cast<llama_token *>(tokens), n_tokens);
    int rc = llama_decode(ctx, batch);

    env->ReleaseIntArrayElements(jtokens, tokens, JNI_ABORT);

    if (rc != 0) {
        LOGE("Decode failed (%d)", rc);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

// ── sampling ────────────────────────────────────────────────────────

JNIEXPORT jint JNICALL
Java_dev_koml_engine_LlamaNative_sampleToken(JNIEnv *, jobject,
                                             jlong ctx_handle,
                                             jfloat temperature,
                                             jfloat top_p, jint top_k) {
    auto *ctx = reinterpret_cast<llama_context *>(ctx_handle);

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_token token = llama_sampler_sample(sampler, ctx, -1);
    llama_sampler_free(sampler);

    return static_cast<jint>(token);
}

// ── token utilities ─────────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_dev_koml_engine_LlamaNative_tokenToPiece(JNIEnv *env, jobject,
                                              jlong model_handle, jint token) {
    auto *model = reinterpret_cast<llama_model *>(model_handle);
    const llama_vocab *vocab = llama_model_get_vocab(model);

    char buf[256];
    int n = llama_token_to_piece(vocab, static_cast<llama_token>(token),
                                 buf, sizeof(buf), 0, true);
    if (n < 0) {
        std::vector<char> large(-n);
        n = llama_token_to_piece(vocab, static_cast<llama_token>(token),
                                 large.data(), static_cast<int>(large.size()),
                                 0, true);
        if (n > 0) {
            return env->NewStringUTF(std::string(large.data(), n).c_str());
        }
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(std::string(buf, n).c_str());
}

JNIEXPORT jboolean JNICALL
Java_dev_koml_engine_LlamaNative_isEogToken(JNIEnv *, jobject,
                                            jlong model_handle, jint token) {
    auto *model = reinterpret_cast<llama_model *>(model_handle);
    const llama_vocab *vocab = llama_model_get_vocab(model);
    return llama_vocab_is_eog(vocab, static_cast<llama_token>(token))
               ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
