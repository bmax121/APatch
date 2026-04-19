use anyhow::Result;
use const_format::concatcp;

use crate::{defs::BINARY_DIR, utils};

pub const RESETPROP_PATH: &str = concatcp!(BINARY_DIR, "resetprop");
pub const BUSYBOX_PATH: &str = concatcp!(BINARY_DIR, "busybox");
pub const MAGISKPOLICY_PATH: &str = concatcp!(BINARY_DIR, "magiskpolicy");

pub fn ensure_binaries() -> Result<()> {
    utils::ensure_binary(BUSYBOX_PATH)?;
    let resetprop_link = RESETPROP_PATH;
    let _ = std::fs::remove_file(resetprop_link);
    std::os::unix::fs::symlink("/data/adb/apd", resetprop_link)?;

    let magiskpolicy_link = MAGISKPOLICY_PATH;
    let _ = std::fs::remove_file(magiskpolicy_link);
    std::os::unix::fs::symlink("/data/adb/apd", magiskpolicy_link)?;

    Ok(())
}
