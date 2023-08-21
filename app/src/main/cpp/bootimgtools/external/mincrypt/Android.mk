# Copyright 2008 The Android Open Source Project
#
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libmincrypt
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_C_INCLUDES)
LOCAL_SRC_FILES := dsa_sig.c p256.c p256_ec.c p256_ecdsa.c rsa.c sha.c sha256.c
include $(BUILD_STATIC_LIBRARY)
