/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2023 bmax121. All Rights Reserved.
 * Copyright (C) 2024 GarfieldHan. All Rights Reserved.
 * Copyright (C) 2024 1f2003d5. All Rights Reserved.
 */

#include <jni.h>
#include <android/log.h>
#include <cstring>

#include "apjni.hpp"
#include "supercall.h"

extern "C" {
    JNIEXPORT jboolean JNICALL
    Java_me_bmax_apatch_Natives_nativeReady(JNIEnv *env, jobject /* this */, jstring superKey)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }

        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        bool rc = sc_ready(skey);
        env->ReleaseStringUTFChars(superKey, skey);
        return rc;
    }

    JNIEXPORT jlong JNICALL Java_me_bmax_apatch_Natives_nativeKernelPatchVersion(JNIEnv *env, jobject /* this */,
                                                                                jstring superKey)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        uint32_t version = sc_kp_ver(skey);
        env->ReleaseStringUTFChars(superKey, skey);
        return version;
    }

    JNIEXPORT jint JNICALL Java_me_bmax_apatch_Natives_nativeSu(JNIEnv *env, jobject /* this */, jstring superKey,
                                                                 jint to_uid, jstring scontext)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        const char *sctx = nullptr;
        if (scontext) sctx = env->GetStringUTFChars(scontext, nullptr);
        struct su_profile profile = { 0 };
        profile.uid = getuid();
        profile.to_uid = (uid_t)to_uid;
        if (sctx) strncpy(profile.scontext, sctx, sizeof(profile.scontext) - 1);
        long rc = sc_su(skey, &profile);
        if (rc < 0) [[unlikely]] {
            LOGE("nativeSu error: %ld\n", rc);
        }
        env->ReleaseStringUTFChars(superKey, skey);
        if (sctx) env->ReleaseStringUTFChars(scontext, sctx);
        return rc;
    }

    JNIEXPORT jlong JNICALL Java_me_bmax_apatch_Natives_nativeThreadSu(JNIEnv *env, jobject /* this */, jstring superKey,
                                                                       jint tid, jstring scontext)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        const char *sctx = nullptr;
        if (scontext) sctx = env->GetStringUTFChars(scontext, nullptr);
        struct su_profile profile = { 0 };
        profile.uid = getuid();
        profile.to_uid = 0;
        if (sctx) strncpy(profile.scontext, sctx, sizeof(profile.scontext) - 1);
        long rc = sc_su_task(skey, tid, &profile);
        env->ReleaseStringUTFChars(superKey, skey);
        env->ReleaseStringUTFChars(scontext, sctx);
        return rc;
    }

    JNIEXPORT jint JNICALL Java_me_bmax_apatch_Natives_nativeSuNums(JNIEnv *env, jobject /* this */, jstring superKey)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        long rc = sc_su_uid_nums(skey);
        env->ReleaseStringUTFChars(superKey, skey);
        return rc;
    }

    JNIEXPORT jintArray JNICALL Java_me_bmax_apatch_Natives_nativeSuUids(JNIEnv *env, jobject /* this */,
                                                                         jstring superKey)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return nullptr;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        int num = sc_su_uid_nums(skey);
        int uids[num];
        long n = sc_su_allow_uids(skey, (uid_t *)uids, num);
        if (n > 0) [[unlikely]] {
            jintArray array = env->NewIntArray(num);
            env->SetIntArrayRegion(array, 0, n, uids);
            return array;
        }
        env->ReleaseStringUTFChars(superKey, skey);
        return env->NewIntArray(0);
    }

    JNIEXPORT jobject JNICALL Java_me_bmax_apatch_Natives_nativeSuProfile(JNIEnv *env, jobject /* this */,
                                                                          jstring superKey, jint uid)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return nullptr;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        struct su_profile profile = { 0 };
        long rc = sc_su_uid_profile(skey, (uid_t)uid, &profile);
        if (rc < 0) [[unlikely]] {
            LOGE("nativeSuProfile error: %ld\n", rc);
            env->ReleaseStringUTFChars(superKey, skey);
            return nullptr;
        }
        jclass cls = env->FindClass("me/bmax/apatch/Natives$Profile");
        jmethodID constructor = env->GetMethodID(cls, "<init>", "()V");
        jfieldID uidField = env->GetFieldID(cls, "uid", "I");
        jfieldID toUidField = env->GetFieldID(cls, "toUid", "I");
        jfieldID scontextFild = env->GetFieldID(cls, "scontext", "Ljava/lang/String;");

        jobject obj = env->NewObject(cls, constructor);
        env->SetIntField(obj, uidField, (int) profile.uid);
        env->SetIntField(obj, toUidField, (int) profile.to_uid);
        env->SetObjectField(obj, scontextFild, env->NewStringUTF(profile.scontext));

        return obj;
    }

    JNIEXPORT jlong JNICALL Java_me_bmax_apatch_Natives_nativeLoadKernelPatchModule(JNIEnv *env, jobject /* this */,
                                                                                    jstring superKey,
                                                                                    jstring modulePath,
                                                                                    jstring jargs)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        const char *path = env->GetStringUTFChars(modulePath, nullptr);
        const char *args = env->GetStringUTFChars(jargs, nullptr);
        long rc = sc_kpm_load(skey, path, args, nullptr);
        if (rc < 0) [[unlikely]] {
            LOGE("nativeLoadKernelPatchModule error: %ld", rc);
        }
        env->ReleaseStringUTFChars(superKey, skey);
        env->ReleaseStringUTFChars(modulePath, path);
        env->ReleaseStringUTFChars(jargs, args);
        return rc;
    }

    JNIEXPORT jobject JNICALL Java_me_bmax_apatch_Natives_nativeControlKernelPatchModule(JNIEnv *env, jobject /* this */,
                                                                                         jstring superKey,
                                                                                         jstring modName,
                                                                                         jstring jctlargs)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return nullptr;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        const char *name = env->GetStringUTFChars(modName, nullptr);
        const char *ctlargs = env->GetStringUTFChars(jctlargs, nullptr);

        char buf[4096] = { '\0' };
        long rc = sc_kpm_control(skey, name, ctlargs, buf, sizeof(buf));
        if (rc < 0) [[unlikely]] {
            LOGE("nativeControlKernelPatchModule error: %ld", rc);
        }

        jclass cls = env->FindClass("me/bmax/apatch/Natives$KPMCtlRes");
        jmethodID constructor = env->GetMethodID(cls, "<init>", "()V");
        jfieldID rcField = env->GetFieldID(cls, "rc", "J");
        jfieldID outMsg = env->GetFieldID(cls, "outMsg", "Ljava/lang/String;");

        jobject obj = env->NewObject(cls, constructor);
        env->SetLongField(obj, rcField, rc);
        env->SetObjectField(obj, outMsg, env->NewStringUTF(buf));

        env->ReleaseStringUTFChars(superKey, skey);
        env->ReleaseStringUTFChars(modName, name);
        env->ReleaseStringUTFChars(jctlargs, ctlargs);
        return obj;
    }

    JNIEXPORT jlong JNICALL Java_me_bmax_apatch_Natives_nativeUnloadKernelPatchModule(JNIEnv *env, jobject /* this */,
                                                                                      jstring superKey,
                                                                                      jstring modName)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        const char *name = env->GetStringUTFChars(modName, nullptr);
        long rc = sc_kpm_unload(skey, name, nullptr);
        if (rc < 0) [[unlikely]] {
            LOGE("nativeUnloadKernelPatchModule error: %ld", rc);
        }

        env->ReleaseStringUTFChars(superKey, skey);
        env->ReleaseStringUTFChars(modName, name);
        return rc;
    }

    JNIEXPORT jlong JNICALL Java_me_bmax_apatch_Natives_nativeKernelPatchModuleNum(JNIEnv *env, jobject /* this */,
                                                                                   jstring superKey)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        long rc = sc_kpm_nums(skey);
        if (rc < 0) [[unlikely]] {
            LOGE("nativeKernelPatchModuleNum error: %ld", rc);
        }

        env->ReleaseStringUTFChars(superKey, skey);
        return rc;
    }

    JNIEXPORT jstring JNICALL Java_me_bmax_apatch_Natives_nativeKernelPatchModuleList(JNIEnv *env, jobject /* this */,
                                                                                      jstring superKey)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return nullptr;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        char buf[4096] = { '\0' };
        long rc = sc_kpm_list(skey, buf, sizeof(buf));
        if (rc < 0) [[unlikely]] {
            LOGE("nativeKernelPatchModuleList error: %ld", rc);
        }

        env->ReleaseStringUTFChars(superKey, skey);
        return env->NewStringUTF(buf);
    }

    JNIEXPORT jstring JNICALL Java_me_bmax_apatch_Natives_nativeKernelPatchModuleInfo(JNIEnv *env, jobject /* this */,
                                                                                      jstring superKey,
                                                                                      jstring modName)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return nullptr;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        const char *name = env->GetStringUTFChars(modName, nullptr);
        char buf[1024] = { '\0' };
        long rc = sc_kpm_info(skey, name, buf, sizeof(buf));
        if (rc < 0) [[unlikely]] {
            LOGE("nativeKernelPatchModuleInfo error: %ld", rc);
        }
        env->ReleaseStringUTFChars(superKey, skey);
        env->ReleaseStringUTFChars(modName, name);
        return env->NewStringUTF(buf);
    }

    JNIEXPORT jlong JNICALL Java_me_bmax_apatch_Natives_nativeGrantSu(JNIEnv *env, jobject /* this */, jstring superKey,
                                                                      jint uid, jint to_uid, jstring scontext)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        const char *sctx = env->GetStringUTFChars(scontext, nullptr);
        struct su_profile profile = { 0 };
        profile.uid = uid;
        profile.to_uid = to_uid;
        if (sctx) strncpy(profile.scontext, sctx, sizeof(profile.scontext) - 1);
        long rc = sc_su_grant_uid(skey, &profile);
        env->ReleaseStringUTFChars(superKey, skey);
        env->ReleaseStringUTFChars(scontext, sctx);
        return rc;
    }

    JNIEXPORT jlong JNICALL Java_me_bmax_apatch_Natives_nativeRevokeSu(JNIEnv *env, jobject /* this */, jstring superKey,
                                                                       jint uid)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        long rc = sc_su_revoke_uid(skey, (uid_t)uid);
        env->ReleaseStringUTFChars(superKey, skey);
        return rc;
    }

    JNIEXPORT jstring JNICALL Java_me_bmax_apatch_Natives_nativeSuPath(JNIEnv *env, jobject /* this */, jstring superKey)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return nullptr;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        char buf[SU_PATH_MAX_LEN] = { '\0' };
        long rc = sc_su_get_path(skey, buf, sizeof(buf));
        if (rc < 0) [[unlikely]] {
            LOGE("nativeSuPath error: %ld", rc);
        }
        env->ReleaseStringUTFChars(superKey, skey);
        return env->NewStringUTF(buf);
    }

    JNIEXPORT jboolean JNICALL Java_me_bmax_apatch_Natives_nativeResetSuPath(JNIEnv *env, jobject /* this */,
                                                                             jstring superKey, jstring jpath)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        const char *path = env->GetStringUTFChars(jpath, nullptr);
        long rc = sc_su_reset_path(skey, path);
        env->ReleaseStringUTFChars(superKey, skey);
        env->ReleaseStringUTFChars(jpath, path);
        return rc == 0;
    }

JNIEXPORT jboolean JNICALL Java_me_bmax_apatch_Natives_nativeGetSafeMode(JNIEnv *env, jobject /* this */,
                                                                         jstring superKey)
    {
        if (!superKey) [[unlikely]] {
            LOGE("Super Key is null!");
            return -EINVAL;
        }
        const char *skey = env->GetStringUTFChars(superKey, nullptr);
        long rc = sc_su_get_safemode(skey);
        env->ReleaseStringUTFChars(superKey, skey);
        return rc == 1;
    }
}
