use const_format::concatcp;

pub const ADB_DIR: &str = "/data/adb/";
pub const WORKING_DIR: &str = concatcp!(ADB_DIR, "ap/");
pub const BINARY_DIR: &str = concatcp!(WORKING_DIR, "bin/");
pub const LOG_DIR: &str = concatcp!(WORKING_DIR, "log/");

pub const AP_RC_PATH: &str = concatcp!(WORKING_DIR, ".aprc");
pub const AP_OVERLAY_SOURCE: &str = "APatch";
pub const DAEMON_PATH: &str = concatcp!(ADB_DIR, "apd");
pub const MAGISK_POLICY_PATH: &str = concatcp!(BINARY_DIR, "magiskpolicy");

#[cfg(target_os = "android")]
pub const SAFEMODE_PATH: &str = "/dev/._safemode";
pub const AP_VERSION_PATH: &str = concatcp!(WORKING_DIR, "version");
pub const DAEMON_LINK_PATH: &str = concatcp!(BINARY_DIR, "apd");
pub const KPATCH_LINK_PATH: &str = concatcp!(BINARY_DIR, "kpatch");
pub const SUPOLICY_LINK_PATH: &str = concatcp!(BINARY_DIR, "supolicy");

pub const MODULE_DIR: &str = concatcp!(ADB_DIR, "modules/");
pub const MODULE_IMG: &str = concatcp!(WORKING_DIR, "modules.img");
pub const MODULE_UPDATE_IMG: &str = concatcp!(WORKING_DIR, "modules_update.img");

pub const MODULE_UPDATE_TMP_IMG: &str = concatcp!(WORKING_DIR, "update_tmp.img");

// warning: this directory should not change, or you need to change the code in module_installer.sh!!!
pub const MODULE_UPDATE_TMP_DIR: &str = concatcp!(ADB_DIR, "modules_update/");

pub const DISABLE_FILE_NAME: &str = "disable";
pub const UPDATE_FILE_NAME: &str = "update";
pub const REMOVE_FILE_NAME: &str = "remove";
pub const SKIP_MOUNT_FILE_NAME: &str = "skip_mount";

pub const VERSION_CODE: &str = include_str!(concat!(env!("OUT_DIR"), "/VERSION_CODE"));
pub const VERSION_NAME: &str = include_str!(concat!(env!("OUT_DIR"), "/VERSION_NAME"));
