use libc::{c_long, syscall};
use log::{info, warn};
use std::ffi::{CStr, CString};
use std::fs::File;
use std::io::{self, Read};

use crate::package::read_ap_package_config;

const MAJOR: c_long = 0;
const MINOR: c_long = 11;
const PATCH: c_long = 0;

const __NR_SUPERCALL: c_long = 45;
const SUPERCALL_KERNELPATCH_VER: c_long = 0x1008;
const SUPERCALL_SU_GRANT_UID: c_long = 0x1100;
const SUPERCALL_SU_RESET_PATH: c_long = 0x1111;

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
    let ver: c_long = unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            ver_and_cmd(SUPERCALL_KERNELPATCH_VER),
        )
    };
    if ver >= 0x0a05 {
        ver_and_cmd(cmd)
    } else {
        hash_key_cmd(key, cmd)
    }
}

fn sc_su_grant_uid(key: &CStr, profile: &SuProfile) -> c_long {
    if key.to_bytes().is_empty() {
        return (-libc::EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            compact_cmd(key, SUPERCALL_SU_GRANT_UID),
            profile,
        )
    }
}

fn sc_su_reset_path(key: &CStr, path: &CStr) -> c_long {
    if key.to_bytes().is_empty() || path.to_bytes().is_empty() {
        return (-libc::EINVAL).into();
    }
    unsafe {
        syscall(
            __NR_SUPERCALL,
            key.as_ptr(),
            compact_cmd(key, SUPERCALL_SU_RESET_PATH),
            path.as_ptr(),
        )
    }
}

fn read_file_to_string(path: &str) -> io::Result<String> {
    let mut file = File::open(path)?;
    let mut content = String::new();
    file.read_to_string(&mut content)?;
    Ok(content)
}

fn convert_string_to_u8_array(s: &String) -> [u8; SUPERCALL_SCONTEXT_LEN] {
    let mut u8_array = [0u8; SUPERCALL_SCONTEXT_LEN];
    let bytes = s.as_bytes();
    let len = usize::min(SUPERCALL_SCONTEXT_LEN, bytes.len());
    u8_array[..len].copy_from_slice(&bytes[..len]);
    u8_array
}

pub fn init_notify_su_uid(superkey: &Option<String>) {
    let package_configs = read_ap_package_config();

    let key = match superkey.as_ref() {
        Some(key_str) => match CString::new(key_str.clone()) {
            Ok(cstr) => Some(cstr),
            Err(e) => {
                warn!("Failed to convert superkey: {}", e);
                None
            }
        },
        None => None,
    };

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

pub fn init_notify_su_path(superkey: &Option<String>) {
    let su_path_file = "/data/adb/ap/su_path";

    match read_file_to_string(su_path_file) {
        Ok(su_path) => {
            let superkey_cstr = match superkey.as_ref() {
                Some(superkey_str) => match CString::new(superkey_str.clone()) {
                    Ok(cstr) => Some(cstr),
                    Err(e) => {
                        warn!("Failed to convert superkey: {}", e);
                        None
                    }
                },
                None => None,
            };

            if let Some(superkey_cstr) = superkey_cstr {
                match CString::new(su_path.trim()) {
                    Ok(su_path_cstr) => {
                        let result = sc_su_reset_path(&superkey_cstr, &su_path_cstr);
                        if result == 0 {
                            info!("Path reset successfully");
                        } else {
                            warn!("Failed to reset path, error code: {}", result);
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
