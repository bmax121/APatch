use const_format::concatcp;

pub const ADB_DIR: &str = "/data/adb/";
pub const WORKING_DIR: &str = concatcp!(ADB_DIR, "ap/");
pub const BINARY_DIR: &str = concatcp!(WORKING_DIR, "bin/");
pub const APATCH_LOG_FOLDER: &str = concatcp!(WORKING_DIR, "log/");

pub const AP_RC_PATH: &str = concatcp!(WORKING_DIR, ".aprc");
pub const GLOBAL_NAMESPACE_FILE: &str = concatcp!(ADB_DIR,".global_namespace_enable");
pub const LITEMODE_FILE: &str = concatcp!(ADB_DIR,".litemode_enable");
pub const OVERLAY_FILE: &str = concatcp!(ADB_DIR,".overlay_enable");
pub const AP_OVERLAY_SOURCE: &str = "APatch";
pub const DAEMON_PATH: &str = concatcp!(ADB_DIR, "apd");

pub const MODULE_DIR: &str = concatcp!(ADB_DIR, "modules/");
pub const MODULE_UPDATE_TMP_IMG: &str = concatcp!(WORKING_DIR, "update_tmp.img");

// warning: this directory should not change, or you need to change the code in module_installer.sh!!!
pub const MODULE_UPDATE_TMP_DIR: &str = concatcp!(ADB_DIR, "modules_update/");
pub const MODULE_MOUNT_DIR: &str = concatcp!(ADB_DIR, "modules_mount/");

pub const SYSTEM_RW_DIR: &str = concatcp!(MODULE_DIR, ".rw/");

pub const TEMP_DIR: &str = "/debug_ramdisk";
pub const TEMP_DIR_LEGACY: &str = "/sbin";

pub const MODULE_WEB_DIR: &str = "webroot";
pub const MODULE_ACTION_SH: &str = "action.sh";
pub const DISABLE_FILE_NAME: &str = "disable";
pub const UPDATE_FILE_NAME: &str = "update";
pub const REMOVE_FILE_NAME: &str = "remove";
pub const SKIP_MOUNT_FILE_NAME: &str = "skip_mount";
pub const PTS_NAME: &str = "pts";

pub const VERSION_CODE: &str = include_str!(concat!(env!("OUT_DIR"), "/VERSION_CODE"));
pub const VERSION_NAME: &str = include_str!(concat!(env!("OUT_DIR"), "/VERSION_NAME"));
