use anyhow::Result;
use const_format::concatcp;

use crate::{defs::BINARY_DIR, utils};

pub const RESETPROP_PATH: &str = concatcp!(BINARY_DIR, "resetprop");
pub const BUSYBOX_PATH: &str = concatcp!(BINARY_DIR, "busybox");
pub const MAGISKPOLICY_PATH: &str = concatcp!(BINARY_DIR, "magiskpolicy");

pub fn ensure_binaries() -> Result<()> {
    utils::ensure_binary(RESETPROP_PATH)?;
    utils::ensure_binary(BUSYBOX_PATH)?;
    utils::ensure_binary(MAGISKPOLICY_PATH)?;
    Ok(())
}
