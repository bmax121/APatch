//
// Created by GarfieldHan on 2024/6/11.
//

#ifndef APATCH_APJNI_HPP
#define APATCH_APJNI_HPP

#include <jni.h>
#include <android/log.h>
#include "jni_helper.hpp"

using namespace lsplant;

#define LOG_TAG "APatchNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)

void ensureSuperKeyNonNull(jstring super_key_jstr) {
    if (!super_key_jstr) [[unlikely]] {
        LOGE("[%s] Super Key is null!", __PRETTY_FUNCTION__);
        abort();
    }
}

#endif //APATCH_APJNI_HPP
