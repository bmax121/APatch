use anyhow::Result;
use const_format::concatcp;
use rust_embed::RustEmbed;

use crate::{defs::BINARY_DIR, utils};

pub const RESETPROP_PATH: &str = concatcp!(BINARY_DIR, "resetprop");
pub const BUSYBOX_PATH: &str = concatcp!(BINARY_DIR, "busybox");
pub const MAGISKPOLICY_PATH: &str = concatcp!(BINARY_DIR, "magiskpolicy");

#[cfg(target_arch = "aarch64")]
#[derive(RustEmbed)]
#[folder = "../app/libs/arm64-v8a"]
struct Asset;

#[cfg(target_arch = "x86_64")]
#[derive(RustEmbed)]
#[folder = "../app/libs/x86_64"]
struct Asset;

pub fn ensure_binaries() -> Result<()> {
    for file in Asset::iter() {
        utils::ensure_binary(
            format!("{BINARY_DIR}{file}"),
            &Asset::get(&file).unwrap().data,
        )?
    }
    Ok(())
}
