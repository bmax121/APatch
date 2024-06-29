use libc::{c_long, execv, fork, pid_t, setenv, syscall, wait, EINVAL};
use log::{info, warn};
use std::ffi::{CStr, CString};
use std::fs::File;
use std::io::{self, Read};
use std::process::exit;
use std::{process, ptr};

use crate::package::read_ap_package_config;

const MAJOR: c_long = 0;
const MINOR: c_long = 11;
const PATCH: c_long = 0;

const __NR_SUPERCALL: c_long = 45;
const SUPERCALL_KERNELPATCH_VER: c_long = 0x1008;
const SUPERCALL_KERNEL_VER: c_long = 0x1009;
const SUPERCALL_SU: c_long = 0x1010;
const SUPERCALL_SU_GRANT_UID: c_long = 0x1100;
const SUPERCALL_SU_RESET_PATH: c_long = 0x1111;
const SUPERCALL_SU_GET_SAFEMODE: c_long = 0x1112;

const SUPERCALL_SCONTEXT_LEN: usize = 0x60;

#[repr(C)]
struct SuProfile {
    uid: i32,
    to_uid: i32,
    scontext: [u8; SUPERCALL_SCONTEXT_LEN],
}

fn hash_key(key: &CStr) -> c_long {
    key.to_bytes().iter().fold(1000000007, |hash, &byte| {
        hash.wrapping_mul(31).wrapping_add(byte as c_long)
    })
}

fn hash_key_cmd(key: &CStr, cmd: c_long) -> c_long {
    (hash_key(key) & 0xFFFF0000) | cmd
}

fn ver_and_cmd(cmd: c_long) -> c_long {
    let version_code: u32 = ((MAJOR << 16) + (MINOR << 8) + PATCH).try_into().unwrap();
    ((version_code as c_long) << 32) | (0x1158 << 16) | (cmd & 0xFFFF)
}

fn compact_cmd(key: &CStr, cmd: c_long) -> c_long {
    let ver = unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            ver_and_cmd(SUPERCALL_KERNELPATCH_VER),
        ) as c_long
    };
    if ver >= 0x0a05 {
        ver_and_cmd(cmd)
    } else {
        hash_key_cmd(key, cmd)
    }
}

fn sc_su_grant_uid(key: &CStr, profile: &SuProfile) -> c_long {
    if key.to_bytes().is_empty() {
        return (-EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            compact_cmd(key, SUPERCALL_SU_GRANT_UID),
            profile,
        ) as c_long
    }
}

pub fn sc_su_get_safemode(key: &CStr) -> c_long {
    if key.to_bytes().is_empty() {
        warn!("[sc_su_get_safemode] null superkey, tell apd we are not in safemode!");
        return 0;
    }

    let key_ptr = key.as_ptr();
    if key_ptr.is_null() {
        warn!("[sc_su_get_safemode] superkey pointer is null!");
        return 0;
    }

    unsafe {
        syscall(
            __NR_SUPERCALL,
            key_ptr,
            compact_cmd(key, SUPERCALL_SU_GET_SAFEMODE),
        ) as c_long
    }
}

fn sc_su(key: &CStr, profile: &SuProfile) -> c_long {
    if key.to_bytes().is_empty() {
        return (-EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            compact_cmd(key, SUPERCALL_SU),
            profile,
        ) as c_long
    }
}

fn sc_su_reset_path(key: &CStr, path: &CStr) -> c_long {
    if key.to_bytes().is_empty() || path.to_bytes().is_empty() {
        return (-EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            compact_cmd(key, SUPERCALL_SU_RESET_PATH),
            path.as_ptr(),
        ) as c_long
    }
}

fn sc_kp_ver(key: &CStr) -> Result<u32, i32> {
    if key.to_bytes().is_empty() {
        return Err(-EINVAL);
    }
    let ret = unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            compact_cmd(key, SUPERCALL_KERNELPATCH_VER),
        )
    };
    Ok(ret as u32)
}

fn sc_k_ver(key: &CStr) -> Result<u32, i32> {
    if key.to_bytes().is_empty() {
        return Err(-EINVAL);
    }
    let ret = unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            compact_cmd(key, SUPERCALL_KERNEL_VER),
        )
    };
    Ok(ret as u32)
}

fn read_file_to_string(path: &str) -> io::Result<String> {
    let mut file = File::open(path)?;
    let mut content = String::new();
    file.read_to_string(&mut content)?;
    Ok(content)
}

fn convert_string_to_u8_array(s: &str) -> [u8; SUPERCALL_SCONTEXT_LEN] {
    let mut u8_array = [0u8; SUPERCALL_SCONTEXT_LEN];
    let bytes = s.as_bytes();
    let len = usize::min(SUPERCALL_SCONTEXT_LEN, bytes.len());
    u8_array[..len].copy_from_slice(&bytes[..len]);
    u8_array
}

fn convert_superkey(s: &Option<String>) -> Option<CString> {
    s.as_ref().and_then(|s| CString::new(s.clone()).ok())
}

pub fn privilege_apd_profile(superkey: &Option<String>) {
    let key = convert_superkey(superkey);

    let all_allow_ctx = "u:r:magisk:s0";
    let profile = SuProfile {
        uid: process::id().try_into().expect("PID conversion failed"),
        to_uid: 0,
        scontext: convert_string_to_u8_array(all_allow_ctx),
    };
    if let Some(ref key) = key {
        let result = sc_su(key, &profile);
        info!("[privilege_apd_profile] result = {}", result);
    }
}

pub fn init_load_su_uid(superkey: &Option<String>) {
    let package_configs = read_ap_package_config();
    let key = convert_superkey(superkey);

    for config in package_configs {
        if config.allow == 1 && config.exclude == 0 {
            if let Some(ref key) = key {
                let profile = SuProfile {
                    uid: config.uid,
                    to_uid: config.to_uid,
                    scontext: convert_string_to_u8_array(&config.sctx),
                };
                let result = sc_su_grant_uid(key, &profile);
                info!("Processed {}: result = {}", config.pkg, result);
            } else {
                warn!("Superkey is None, skipping config: {}", config.pkg);
            }
        }
    }
}

pub fn init_load_su_path(superkey: &Option<String>) {
    let su_path_file = "/data/adb/ap/su_path";

    match read_file_to_string(su_path_file) {
        Ok(su_path) => {
            let superkey_cstr = convert_superkey(superkey);

            if let Some(superkey_cstr) = superkey_cstr {
                match CString::new(su_path.trim()) {
                    Ok(su_path_cstr) => {
                        let result = sc_su_reset_path(&superkey_cstr, &su_path_cstr);
                        if result == 0 {
                            info!("suPath load successfully");
                        } else {
                            warn!("Failed to load su path, error code: {}", result);
                        }
                    }
                    Err(e) => {
                        warn!("Failed to convert su_path: {}", e);
                    }
                }
            } else {
                warn!("Superkey is None, skipping...");
            }
        }
        Err(e) => {
            warn!("Failed to read su_path file: {}", e);
        }
    }
}

fn set_env_var(key: &str, value: &str) {
    let key_c = CString::new(key).expect("CString::new failed");
    let value_c = CString::new(value).expect("CString::new failed");
    unsafe {
        setenv(key_c.as_ptr(), value_c.as_ptr(), 1);
    }
}

pub fn fork_for_result(exec: &str, argv: &[&str], key: &Option<String>) {
    let mut cmd = String::new();
    for arg in argv {
        cmd.push_str(arg);
        cmd.push(' ');
    }

    let superkey_cstr = convert_superkey(key);

    if let Some(superkey_cstr) = superkey_cstr {
        unsafe {
            let pid: pid_t = fork();
            if pid < 0 {
                //log_kernel("%d fork %s error: %d\n", libc::getpid(), exec, pid);
            } else if pid == 0 {
                set_env_var("KERNELPATCH", "true");
                let kpver = format!("{:x}", sc_kp_ver(&superkey_cstr).unwrap_or(0));
                set_env_var("KERNELPATCH_VERSION", kpver.as_str());
                let kver = format!("{:x}", sc_k_ver(&superkey_cstr).unwrap_or(0));
                set_env_var("KERNEL_VERSION", kver.as_str());
                //setenv("SUPERKEY", key.as_ptr(), 1);
                let c_exec = CString::new(exec).expect("CString::new failed");
                let c_argv: Vec<CString> =
                    argv.iter().map(|&arg| CString::new(arg).unwrap()).collect();
                let mut c_argv_ptrs: Vec<*const libc::c_char> =
                    c_argv.iter().map(|arg| arg.as_ptr()).collect();
                c_argv_ptrs.push(ptr::null());

                execv(c_exec.as_ptr(), c_argv_ptrs.as_ptr());
                //log_kernel("%d exec %s error: %s\n", libc::getpid(), cmd, strerror(errno));
                exit(1); // execv only returns on error
            } else {
                let mut status: libc::c_int = 0;
                wait(&mut status);
                //log_kernel("%d wait %s status: 0x%x\n", libc::getpid(), cmd, status);
            }
        }
    } else {
        warn!("[fork_for_result] SuperKey convert failed!");
    }
}
