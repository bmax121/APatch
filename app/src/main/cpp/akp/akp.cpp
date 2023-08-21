#include <jni.h>
#include <supercall.h>
#include <string>
#include <android/log.h>

#define TAG "akpatch-native"

#define LOGD(fmt, ...) __android_log_print(ANDROID_LOG_INFO, TAG, fmt, __VA_ARGS__);

extern "C"
JNIEXPORT jlong JNICALL
Java_me_bmax_akpatch_Natives_nativeHello(JNIEnv *env, jobject thiz, jstring superKey) {
    const char *skey = env->GetStringUTFChars(superKey, NULL);
    long kv = sc_hello(skey);
    env->ReleaseStringUTFChars(superKey, skey);
    return kv;
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_me_bmax_akpatch_Natives_nativeInstalled(JNIEnv *env, jobject thiz, jstring superKey) {
    const char *skey = env->GetStringUTFChars(superKey, NULL);
    bool rc = sc_hello(skey) == SUPERCALL_HELLO_MAGIC;
    env->ReleaseStringUTFChars(superKey, skey);
    return rc;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_me_bmax_akpatch_Natives_nativeKernelVersion(JNIEnv *env, jobject thiz, jstring superKey) {
    const char *skey = env->GetStringUTFChars(superKey, NULL);
    long kv = sc_get_kernel_version(skey);
    env->ReleaseStringUTFChars(superKey, skey);
    return kv;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_me_bmax_akpatch_Natives_nativeKernelPatchVersion(JNIEnv *env, jobject thiz, jstring superKey) {
    const char *skey = env->GetStringUTFChars(superKey, NULL);
    int kv = sc_get_kp_version(skey);
    env->ReleaseStringUTFChars(superKey, skey);
    return kv;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_me_bmax_akpatch_Natives_nativeLoadKernelPatchModule(JNIEnv *env, jobject thiz, jstring superKey, jstring modulePath) {
    const char *skey = env->GetStringUTFChars(superKey, NULL);
    const char *path = env->GetStringUTFChars(modulePath, NULL);
    int kv = sc_load_kpm(skey, path);
    env->ReleaseStringUTFChars(superKey, skey);
    env->ReleaseStringUTFChars(modulePath, path);
    return kv;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_me_bmax_akpatch_Natives_nativeUnloadKernelPatchModule(JNIEnv *env, jobject thiz, jstring superKey, jstring modulePath) {
    const char *skey = env->GetStringUTFChars(superKey, NULL);
    const char *path = env->GetStringUTFChars(modulePath, NULL);
    int kv = sc_unload_kpm(skey, path);
    env->ReleaseStringUTFChars(superKey, skey);
    env->ReleaseStringUTFChars(modulePath, path);
    return kv;
}


extern "C"
JNIEXPORT jlong JNICALL
Java_me_bmax_akpatch_Natives_nativeMakeMeSu(JNIEnv *env, jobject thiz, jstring superKey) {
    const char *skey = env->GetStringUTFChars(superKey, NULL);
    int kv = sc_su(skey);
    env->ReleaseStringUTFChars(superKey, skey);
    return kv;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_me_bmax_akpatch_Natives_nativeThreadSu(JNIEnv *env, jobject thiz, jstring superKey, jint tid) {
    const char *skey = env->GetStringUTFChars(superKey, NULL);
    int kv = sc_thread_su(skey, tid);
    env->ReleaseStringUTFChars(superKey, skey);
    return kv;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_me_bmax_akpatch_Natives_nativeThreadUnsu(JNIEnv *env, jobject thiz, jstring superKey, jint tid) {
    const char *skey = env->GetStringUTFChars(superKey, NULL);
    int kv = sc_thread_unsu(skey, tid);
    env->ReleaseStringUTFChars(superKey, skey);
    return kv;
}


extern "C"
JNIEXPORT jlong JNICALL
Java_me_bmax_akpatch_Natives_becomeManager(JNIEnv *env, jobject thiz, jstring superKey) {
    // todo
    return -1;
}