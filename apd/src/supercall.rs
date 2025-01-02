use crate::package::{read_ap_package_config, synchronize_package_uid};
use errno::errno;
use libc::{c_int, c_long, c_void, execv, fork, pid_t, setenv, syscall, uid_t, wait, EINVAL};
use log::{error, info, warn};
use std::ffi::{CStr, CString};
use std::fmt::Write;
use std::fs::File;
use std::io::{self, Read};


use std::process::exit;
use std::sync::{Arc, Mutex};


use std::{process, ptr};

const MAJOR: c_long = 0;
const MINOR: c_long = 11;
const PATCH: c_long = 1;

const KSTORAGE_EXCLUDE_LIST_GROUP: i32 = 1;

const __NR_SUPERCALL: c_long = 45;
const SUPERCALL_KLOG: c_long = 0x1004;
const SUPERCALL_KERNELPATCH_VER: c_long = 0x1008;
const SUPERCALL_KERNEL_VER: c_long = 0x1009;
const SUPERCALL_SU: c_long = 0x1010;
const SUPERCALL_KSTORAGE_WRITE: c_long = 0x1041;
const SUPERCALL_SU_GRANT_UID: c_long = 0x1100;
const SUPERCALL_SU_REVOKE_UID: c_long = 0x1101;
const SUPERCALL_SU_NUMS: c_long = 0x1102;
const SUPERCALL_SU_LIST: c_long = 0x1103;
const SUPERCALL_SU_RESET_PATH: c_long = 0x1111;
const SUPERCALL_SU_GET_SAFEMODE: c_long = 0x1112;
const SUPERCALL_KPM_LOAD: c_long = 0x1020;
const SUPERCALL_KPM_LIST: c_long = 0x1031;
const SUPERCALL_KPM_INFO: c_long = 0x1032;

const SUPERCALL_SCONTEXT_LEN: usize = 0x60;

#[repr(C)]
struct SuProfile {
    uid: i32,
    to_uid: i32,
    scontext: [u8; SUPERCALL_SCONTEXT_LEN],
}

fn ver_and_cmd(cmd: c_long) -> c_long {
    let version_code: u32 = ((MAJOR << 16) + (MINOR << 8) + PATCH).try_into().unwrap();
    ((version_code as c_long) << 32) | (0x1158 << 16) | (cmd & 0xFFFF)
}

fn sc_su_revoke_uid(key: &CStr, uid: uid_t) -> c_long {
    if key.to_bytes().is_empty() {
        return (-EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            ver_and_cmd(SUPERCALL_SU_REVOKE_UID),
            uid,
        ) as c_long
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
            ver_and_cmd(SUPERCALL_SU_GRANT_UID),
            profile,
        ) as c_long
    }
}

fn sc_kstorage_write(
    key: &CStr,
    gid: i32,
    did: i64,
    data: *mut c_void,
    offset: i32,
    dlen: i32,
) -> c_long {
    if key.to_bytes().is_empty() {
        return (-EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            ver_and_cmd(SUPERCALL_KSTORAGE_WRITE),
            gid as c_long,
            did as c_long,
            data,
            (((offset as i64) << 32) | (dlen as i64)) as c_long,
        ) as c_long
    }
}

fn sc_set_ap_mod_exclude(key: &CStr, uid: i64, exclude: i32) -> c_long {
    sc_kstorage_write(
        key,
        KSTORAGE_EXCLUDE_LIST_GROUP,
        uid,
        &exclude as *const i32 as *mut c_void,
        0,
        std::mem::size_of::<i32>() as i32,
    )
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
            ver_and_cmd(SUPERCALL_SU_GET_SAFEMODE),
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
            ver_and_cmd(SUPERCALL_SU),
            profile,
        ) as c_long
    }
}
pub fn sc_kpm_load(key: &CStr, path: &CStr, args: Option<&CStr>, reserved: *mut c_void) -> c_long {
    if key.to_bytes().is_empty() || path.to_bytes().is_empty() {
        return (-EINVAL).into();
    }

    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            ver_and_cmd(SUPERCALL_KPM_LOAD),
            path.as_ptr(),
            args.map_or(std::ptr::null(), |a| a.as_ptr()),
            reserved,
        ) as c_long
    }
}
pub fn sc_kpm_list(key: &CStr, names_buf: &mut [u8]) -> c_long {
    if key.to_bytes().is_empty() {
        return (-EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            ver_and_cmd(SUPERCALL_KPM_LIST),
            names_buf.as_mut_ptr(),
            names_buf.len() as i32,
        ) as c_long
    }
}
pub fn sc_kpm_info(key: &CStr, name: &CStr, info_buf: &mut [u8]) -> c_long {
    if key.to_bytes().is_empty() {
        return (-EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            ver_and_cmd(SUPERCALL_KPM_INFO),
            name.as_ptr(),
            info_buf.as_mut_ptr(),
            info_buf.len() as i32,
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
            ver_and_cmd(SUPERCALL_SU_RESET_PATH),
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
            ver_and_cmd(SUPERCALL_KERNELPATCH_VER),
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
            ver_and_cmd(SUPERCALL_KERNEL_VER),
        )
    };
    Ok(ret as u32)
}

fn sc_klog(key: &CStr, msg: &CStr) -> c_long {
    if key.to_bytes().is_empty() || msg.to_bytes().is_empty() {
        return (-EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            ver_and_cmd(SUPERCALL_KLOG),
            msg.as_ptr(),
        ) as c_long
    }
}

fn sc_su_uid_nums(key: &CStr) -> c_long {
    if key.to_bytes().is_empty() {
        return (-EINVAL).into();
    }
    unsafe { syscall(__NR_SUPERCALL, key.as_ptr(), ver_and_cmd(SUPERCALL_SU_NUMS)) as c_long }
}

fn sc_su_allow_uids(key: &CStr, buf: &mut [uid_t]) -> c_long {
    if key.to_bytes().is_empty() {
        return (-EINVAL).into();
    }
    if buf.is_empty() {
        return (-EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            ver_and_cmd(SUPERCALL_SU_LIST),
            buf.as_mut_ptr(),
            buf.len() as i32,
        ) as c_long
    }
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

pub fn refresh_ap_package_list(skey: &CStr, mutex: &Arc<Mutex<()>>) {
    let _lock = mutex.lock().unwrap();

    let num = sc_su_uid_nums(skey);
    if num < 0 {
        error!("[refresh_su_list] Error getting number of UIDs: {}", num);
        return;
    }
    let num = num as usize;
    let mut uids = vec![0 as uid_t; num];
    let n = sc_su_allow_uids(skey, &mut uids);
    if n < 0 {
        error!("[refresh_su_list] Error getting su list");
        return;
    }
    for uid in &uids {
        if *uid == 0 || *uid == 2000 {
            warn!(
                "[refresh_ap_package_list] Skip revoking critical uid: {}",
                uid
            );
            continue;
        }
        info!(
            "[refresh_ap_package_list] Revoking {} root permission...",
            uid
        );
        let rc = sc_su_revoke_uid(skey, *uid);
        if rc != 0 {
            error!("[refresh_ap_package_list] Error revoking UID: {}", rc);
        }
    }

    synchronize_package_uid();

    let package_configs = read_ap_package_config();
    for config in package_configs {
        if config.allow == 1 && config.exclude == 0 {
            let profile = SuProfile {
                uid: config.uid,
                to_uid: config.to_uid,
                scontext: convert_string_to_u8_array(&config.sctx),
            };
            let result = sc_su_grant_uid(skey, &profile);
            info!(
                "[refresh_ap_package_list] Loading {}: result = {}",
                config.pkg, result
            );
        }
        if config.allow == 0 && config.exclude == 1 {
            let result = sc_set_ap_mod_exclude(skey, config.uid as i64, 1);
            info!(
                "[refresh_ap_package_list] Loading exclude {}: result = {}",
                config.pkg, result
            );
        }
    }
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

pub fn init_load_package_uid_config(superkey: &Option<String>) {
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
        if config.allow == 0 && config.exclude == 1 {
            if let Some(ref key) = key {
                let result = sc_set_ap_mod_exclude(key, config.uid as i64, 1);
                info!("Processed exclude {}: result = {}", config.pkg, result);
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

fn log_kernel(key: &CStr, _fmt: &str, args: std::fmt::Arguments) -> c_long {
    let mut buf = String::with_capacity(1024);
    write!(&mut buf, "{}", args).expect("Error formatting string");

    let c_buf = CString::new(buf).expect("CString::new failed");
    sc_klog(key, &c_buf)
}

#[macro_export]
macro_rules! log_kernel {
    ($key:expr, $fmt:expr, $($arg:tt)*) => (
        log_kernel($key, $fmt, std::format_args!($fmt, $($arg)*))
    )
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
                log_kernel!(
                    &superkey_cstr,
                    "{} fork {} error: {}\n",
                    libc::getpid(),
                    exec,
                    -1
                );
            } else if pid == 0 {
                set_env_var("KERNELPATCH", "true");
                let kpver = format!("{:x}", sc_kp_ver(&superkey_cstr).unwrap_or(0));
                set_env_var("KERNELPATCH_VERSION", kpver.as_str());
                let kver = format!("{:x}", sc_k_ver(&superkey_cstr).unwrap_or(0));
                set_env_var("KERNEL_VERSION", kver.as_str());

                let c_exec = CString::new(exec).expect("CString::new failed");
                let c_argv: Vec<CString> =
                    argv.iter().map(|&arg| CString::new(arg).unwrap()).collect();
                let mut c_argv_ptrs: Vec<*const libc::c_char> =
                    c_argv.iter().map(|arg| arg.as_ptr()).collect();
                c_argv_ptrs.push(ptr::null());

                execv(c_exec.as_ptr(), c_argv_ptrs.as_ptr());

                log_kernel!(
                    &superkey_cstr,
                    "{} exec {} error: {}\n",
                    libc::getpid(),
                    cmd,
                    CStr::from_ptr(libc::strerror(errno().0))
                        .to_string_lossy()
                        .into_owned()
                );
                exit(1); // execv only returns on error
            } else {
                let mut status: c_int = 0;
                wait(&mut status);
                log_kernel!(
                    &superkey_cstr,
                    "{} wait {} status: 0x{}\n",
                    libc::getpid(),
                    cmd,
                    status
                );
            }
        }
    } else {
        warn!("[fork_for_result] SuperKey convert failed!");
    }
}
