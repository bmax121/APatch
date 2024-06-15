/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2023 bmax121. All Rights Reserved.
 */

#ifndef _KPU_SUPERCALL_H_
#define _KPU_SUPERCALL_H_

#include <unistd.h>
#include <sys/syscall.h>
#include <stdbool.h>
#include <stddef.h>
#include <string.h>
#include <errno.h>

#include "uapi/scdefs.h"
#include "version"

/// @deprecated
/// KernelPatch version less than 0xa05
static inline long hash_key_cmd(const char *key, long cmd)
{
    long hash = hash_key(key);
    return (hash & 0xFFFF0000) | cmd;
}

/// KernelPatch version is greater than or equal to 0x0a05
static inline long ver_and_cmd(const char *key, long cmd)
{
    uint32_t version_code = (MAJOR << 16) + (MINOR << 8) + PATCH;
    return ((long)version_code << 32) | (0x1158 << 16) | (cmd & 0xFFFF);
}

static inline long compact_cmd(const char *key, long cmd)
{
#if 1
    long ver = syscall(__NR_supercall, key, ver_and_cmd(key, SUPERCALL_KERNELPATCH_VER));
    if (ver >= 0xa05) return ver_and_cmd(key, cmd);
#endif
    return hash_key_cmd(key, cmd);
}

/**
 * @brief If KernelPatch installed, @see SUPERCALL_HELLO_ECHO will echoed.
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @return long 
 */
static inline long sc_hello(const char *key)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_HELLO));
    return ret;
}

/**
 * @brief Is KernelPatch installed?
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @return true 
 * @return false 
 */
static inline bool sc_ready(const char *key)
{
    return sc_hello(key) == SUPERCALL_HELLO_MAGIC;
}

/**
 * @brief Print messages by printk in the kernel
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @param msg 
 * @return long 
 */
static inline long sc_klog(const char *key, const char *msg)
{
    if (!key || !key[0]) return -EINVAL;
    if (!msg || strlen(msg) <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_KLOG), msg);
    return ret;
}

/**
 * @brief KernelPatch version number
 * 
 * @param key 
 * @return uint32_t 
 */
static inline uint32_t sc_kp_ver(const char *key)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_KERNELPATCH_VER));
    return (uint32_t)ret;
}

/**
 * @brief Kernel version number
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @return uint32_t 
 */
static inline uint32_t sc_k_ver(const char *key)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_KERNEL_VER));
    return (uint32_t)ret;
}

/**
 * @brief Substitute user of current thread
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @param profile : if scontext is invalid or illegal, all selinux permission checks will bypass via hook
 * @see struct su_profile
 * @return long : 0 if succeed
 */
static inline long sc_su(const char *key, struct su_profile *profile)
{
    if (!key || !key[0]) return -EINVAL;
    if (strlen(profile->scontext) >= SUPERCALL_SCONTEXT_LEN) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU), profile);
    return ret;
}

/**
 * @brief Substitute user of tid specfied
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @param tid : target thread id
 * @param profile : if scontext is invalid or illegal, all selinux permission checks will bypass via hook
 * @see struct su_profile
 * @return long : 0 if succeed 
 */
static inline long sc_su_task(const char *key, pid_t tid, struct su_profile *profile)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_TASK), tid, profile);
    return ret;
}

/**
 * @brief Grant su permission
 * 
 * @param key 
 * @param profile : if scontext is invalid or illegal, all selinux permission checks will bypass via hook
 * @return long : 0 if succeed
 */
static inline long sc_su_grant_uid(const char *key, struct su_profile *profile)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_GRANT_UID), profile);
    return ret;
}

/**
 * @brief Revoke su permission
 * 
 * @param key 
 * @param uid 
 * @return long 0 if succeed
 */
static inline long sc_su_revoke_uid(const char *key, uid_t uid)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_REVOKE_UID), uid);
    return ret;
}

/**
 * @brief Get numbers of su allowed uids
 * 
 * @param key 
 * @return long 
 */
static inline long sc_su_uid_nums(const char *key)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_NUMS));
    return ret;
}

/**
 * @brief 
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @param buf 
 * @param num 
 * @return long : The numbers of uids if succeed, nagative value if failed
 */
static inline long sc_su_allow_uids(const char *key, uid_t *buf, int num)
{
    if (!key || !key[0]) return -EINVAL;
    if (!buf || num <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_LIST), buf, num);
    return ret;
}

/**
 * @brief Get su profile of specified uid
 * 
 * @param key 
 * @param uid 
 * @param out_profile 
 * @return long : 0 if succeed
 */
static inline long sc_su_uid_profile(const char *key, uid_t uid, struct su_profile *out_profile)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_PROFILE), uid, out_profile);
    return ret;
}

/**
 * @brief Get full path of current 'su' command 
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed 
 * @param out_path 
 * @param path_len 
 * @return long : The length of result string if succeed, negative if failed
 */
static inline long sc_su_get_path(const char *key, char *out_path, int path_len)
{
    if (!key || !key[0]) return -EINVAL;
    if (!out_path || path_len <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_GET_PATH), out_path, path_len);
    return ret;
}

/**
 * @brief Reset full path of 'su' command 
 * 
 * @param key 
 * @param path 
 * @return long : 0 if succeed
 */
static inline long sc_su_reset_path(const char *key, const char *path)
{
    if (!key || !key[0]) return -EINVAL;
    if (!path || !path[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_RESET_PATH), path);
    return ret;
}

/**
 * @brief Get current all-allowed selinux context
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed  
 * @param out_sctx 
 * @param sctx_len
 * @return long 0 if there is a all-allowed selinux context now
 */
static inline long sc_su_get_all_allow_sctx(const char *key, char *out_sctx, int sctx_len)
{
    if (!key || !key[0]) return -EINVAL;
    if (!out_sctx) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_GET_ALLOW_SCTX), out_sctx);
    return ret;
}

/**
 * @brief Reset current all-allowed selinux context
 * 
 * @param key : superkey or 'su' string if caller uid is su allowed  
 * @param sctx If sctx is empty string, clear all-allowed selinux, 
 * otherwise, try to reset a new all-allowed selinux context
 * @return long 0 if succeed
 */
static inline long sc_su_reset_all_allow_sctx(const char *key, const char *sctx)
{
    if (!key || !key[0]) return -EINVAL;
    if (!sctx) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_SET_ALLOW_SCTX), sctx);
    return ret;
}

/**
 * @brief Load module
 * 
 * @param key : superkey
 * @param path 
 * @param args 
 * @param reserved 
 * @return long : 0 if succeed
 */
static inline long sc_kpm_load(const char *key, const char *path, const char *args, void *reserved)
{
    if (!key || !key[0]) return -EINVAL;
    if (!path || strlen(path) <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_KPM_LOAD), path, args, reserved);
    return ret;
}

/**
 * @brief Control module with arguments 
 * 
 * @param key : superkey
 * @param name : module name
 * @param ctl_args : control argument
 * @param out_msg : output message buffer
 * @param outlen : buffer length of out_msg
 * @return long : 0 if succeed
 */
static inline long sc_kpm_control(const char *key, const char *name, const char *ctl_args, char *out_msg, long outlen)
{
    if (!key || !key[0]) return -EINVAL;
    if (!name || strlen(name) <= 0) return -EINVAL;
    if (!ctl_args || strlen(ctl_args) <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_KPM_CONTROL), name, ctl_args, out_msg, outlen);
    return ret;
}

/**
 * @brief Unload module
 * 
 * @param key : superkey
 * @param name : module name
 * @param reserved 
 * @return long : 0 if succeed
 */
static inline long sc_kpm_unload(const char *key, const char *name, void *reserved)
{
    if (!key || !key[0]) return -EINVAL;
    if (!name || strlen(name) <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_KPM_UNLOAD), name, reserved);
    return ret;
}

/**
 * @brief Current loaded module numbers
 * 
 * @param key : superkey
 * @return long
 */
static inline long sc_kpm_nums(const char *key)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_KPM_NUMS));
    return ret;
}

/**
 * @brief List names of current loaded modules, splited with '\n'
 * 
 * @param key : superkey
 * @param names_buf : output buffer
 * @param buf_len : the length of names_buf
 * @return long : the length of result string if succeed, negative if failed
 */
static inline long sc_kpm_list(const char *key, char *names_buf, int buf_len)
{
    if (!key || !key[0]) return -EINVAL;
    if (!names_buf || buf_len <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_KPM_LIST), names_buf, buf_len);
    return ret;
}

/**
 * @brief Get module information. 
 * 
 * @param key : superkey
 * @param name : module name
 * @param buf : 
 * @param buf_len : 
 * @return long : The length of result string if succeed, negative if failed
 */
static inline long sc_kpm_info(const char *key, const char *name, char *buf, int buf_len)
{
    if (!key || !key[0]) return -EINVAL;
    if (!buf || buf_len <= 0) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_KPM_INFO), name, buf, buf_len);
    return ret;
}

/**
 * @brief Get current superkey
 * 
 * @param key : superkey
 * @param out_key 
 * @param outlen 
 * @return long : 0 if succeed
 */
static inline long sc_skey_get(const char *key, char *out_key, int outlen)
{
    if (!key || !key[0]) return -EINVAL;
    if (outlen < SUPERCALL_KEY_MAX_LEN) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SKEY_GET), out_key, outlen);
    return ret;
}

/**
 * @brief Reset current superkey
 * 
 * @param key : superkey
 * @param new_key 
 * @return long : 0 if succeed
 */
static inline long sc_skey_set(const char *key, const char *new_key)
{
    if (!key || !key[0]) return -EINVAL;
    if (!new_key || !new_key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SKEY_SET), new_key);
    return ret;
}

/**
 * @brief Whether to enable hash verification for root superkey.
 * 
 * @param key : superkey
 * @param enable 
 * @return long 
 */
static inline long sc_skey_root_enable(const char *key, bool enable)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SKEY_ROOT_ENABLE), (long)enable);
    return ret;
}

/**
 * @brief Get whether in safe mode
 *
 * @param key
 * @return long
 */
static inline long sc_su_get_safemode(const char *key)
{
    if (!key || !key[0]) return -EINVAL;
    return syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_SU_GET_SAFEMODE));
}

// todo
static inline long sc_pid_virt_to_phys(const char *key, pid_t pid, unsigned long vaddr)
{
    if (!key || !key[0]) return -EINVAL;
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_MEM_PHYS), pid, vaddr);
    return ret;
}

static inline long sc_bootlog(const char *key)
{
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_BOOTLOG));
    return ret;
}

static inline long sc_panic(const char *key)
{
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_PANIC));
    return ret;
}

static inline long __sc_test(const char *key, long a1, long a2, long a3)
{
    long ret = syscall(__NR_supercall, key, compact_cmd(key, SUPERCALL_TEST), a1, a2, a3);
    return ret;
}

#endif