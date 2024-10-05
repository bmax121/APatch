/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2023 bmax121. All Rights Reserved.
 */

#ifndef _KP_UAPI_SCDEF_H_
#define _KP_UAPI_SCDEF_H_

static inline long hash_key(const char *key)
{
    long hash = 1000000007;
    for (int i = 0; key[i]; i++) {
        hash = hash * 31 + key[i];
    }
    return hash;
}

#define SUPERCALL_HELLO_ECHO "hello1158"

// #define __NR_supercall __NR3264_truncate // 45
#define __NR_supercall 45

#define SUPERCALL_HELLO 0x1000
#define SUPERCALL_KLOG 0x1004

#define SUPERCALL_KERNELPATCH_VER 0x1008
#define SUPERCALL_KERNEL_VER 0x1009

#define SUPERCALL_SKEY_GET 0x100a
#define SUPERCALL_SKEY_SET 0x100b
#define SUPERCALL_SKEY_ROOT_ENABLE 0x100c

#define SUPERCALL_SU 0x1010
#define SUPERCALL_SU_TASK 0x1011 // syscall(__NR_gettid)

#define SUPERCALL_KPM_LOAD 0x1020
#define SUPERCALL_KPM_UNLOAD 0x1021
#define SUPERCALL_KPM_CONTROL 0x1022

#define SUPERCALL_KPM_NUMS 0x1030
#define SUPERCALL_KPM_LIST 0x1031
#define SUPERCALL_KPM_INFO 0x1032

struct kernel_storage
{
    void *data;
    int len;
};

#define SUPERCALL_KSTORAGE_ALLOC_GROUP 0x1040
#define SUPERCALL_KSTORAGE_WRITE 0x1041
#define SUPERCALL_KSTORAGE_READ 0x1042
#define SUPERCALL_KSTORAGE_LIST_IDS 0x1043
#define SUPERCALL_KSTORAGE_REMOVE 0x1044
#define SUPERCALL_KSTORAGE_REMOVE_GROUP 0x1045

#define KSTORAGE_SU_LIST_GROUP 0
#define KSTORAGE_EXCLUDE_LIST_GROUP 1
#define KSTORAGE_UNUSED_GROUP_2 2
#define KSTORAGE_UNUSED_GROUP_3 3

#define SUPERCALL_BOOTLOG 0x10fd
#define SUPERCALL_PANIC 0x10fe
#define SUPERCALL_TEST 0x10ff

#define SUPERCALL_KEY_MAX_LEN 0x40
#define SUPERCALL_SCONTEXT_LEN 0x60

struct su_profile
{
    uid_t uid;
    uid_t to_uid;
    char scontext[SUPERCALL_SCONTEXT_LEN];
};

#ifdef ANDROID
#define SH_PATH "/system/bin/sh"
#define SU_PATH "/system/bin/kp"
#define LEGACY_SU_PATH "/system/bin/su"
#define ECHO_PATH "/system/bin/echo"
#define KERNELPATCH_DATA_DIR "/data/adb/kp"
#define KERNELPATCH_MODULE_DATA_DIR KERNELPATCH_DATA_DIR "/modules"
#define APD_PATH "/data/adb/apd"
#define ALL_ALLOW_SCONTEXT "u:r:kp:s0"
#define ALL_ALLOW_SCONTEXT_MAGISK "u:r:magisk:s0"
#define ALL_ALLOW_SCONTEXT_KERNEL "u:r:kernel:s0"
#else
#define SH_PATH "/usr/bin/sh"
#define ECHO_PATH "/usr/bin/echo"
#define SU_PATH "/usr/bin/kp"
#define ALL_ALLOW_SCONTEXT "u:r:kernel:s0"
#endif

#define SU_PATH_MAX_LEN 128

#define SUPERCMD "/system/bin/truncate"

#define SAFE_MODE_FLAG_FILE "/dev/.safemode"

#define SUPERCALL_SU_GRANT_UID 0x1100
#define SUPERCALL_SU_REVOKE_UID 0x1101
#define SUPERCALL_SU_NUMS 0x1102
#define SUPERCALL_SU_LIST 0x1103
#define SUPERCALL_SU_PROFILE 0x1104
#define SUPERCALL_SU_GET_ALLOW_SCTX 0x1105
#define SUPERCALL_SU_SET_ALLOW_SCTX 0x1106
#define SUPERCALL_SU_GET_PATH 0x1110
#define SUPERCALL_SU_RESET_PATH 0x1111
#define SUPERCALL_SU_GET_SAFEMODE 0x1112

#define SUPERCALL_MAX 0x1200

#define SUPERCALL_RES_SUCCEED 0

#define SUPERCALL_HELLO_MAGIC 0x11581158

#endif
