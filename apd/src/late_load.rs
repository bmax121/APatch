//! Jailbreak mode late-load: load a KernelPatch kernel module on a stock kernel
//! and apply Magisk SELinux policy, mirroring KernelSU's `late-load` flow.

use anyhow::{Result, bail};
use log::{info, warn};
use regex_lite::Regex;
use std::ffi::CStr;
use std::path::{Path, PathBuf};
use std::process::Command;

use crate::{insmod, sepolicy};

/// Parse a Kernel Module Interface (KMI) name like `android14-5.15` from a
/// kernel release string, e.g. `5.15.123-android14-4-g12345678-abcd1234`.
fn parse_kmi(release: &str) -> Option<String> {
    let re = Regex::new(r"(.* )?(\d+\.\d+)(\S+)?(android\d+)(.*)").ok()?;
    let caps = re.captures(release)?;
    let kernel = caps.get(2)?.as_str();
    let android = caps.get(4)?.as_str();
    Some(format!("{android}-{kernel}"))
}

fn kernel_release() -> Option<String> {
    let mut uts: libc::utsname = unsafe { std::mem::zeroed() };
    if unsafe { libc::uname(&mut uts) } != 0 {
        return None;
    }
    let release = unsafe { CStr::from_ptr(uts.release.as_ptr()) };
    Some(release.to_string_lossy().into_owned())
}

pub fn detect_kmi() -> Option<String> {
    parse_kmi(&kernel_release()?)
}

/// `apd late-load [--module <path>] [--kmi <androidX-Y.Z>] [--package-name <pkg>]`
///
/// Loads the KernelPatch module for the device's kernel (auto-detecting the KMI
/// and looking for `/data/adb/ap/{kmi}_kernelpatch.ko` when no module is given),
/// then applies Magisk SELinux policy live and restarts the manager app so it
/// re-initializes with the freshly granted root.
pub fn run(module: Option<PathBuf>, kmi: Option<String>, package_name: Option<String>) -> Result<()> {
    let module = match module {
        Some(path) => path,
        None => {
            let kmi = match kmi.or_else(detect_kmi) {
                Some(kmi) => kmi,
                None => bail!("cannot detect kernel KMI (androidX-Y.Z)"),
            };
            info!("detected KMI: {kmi}");
            let path = PathBuf::from(format!("/data/adb/ap/{kmi}_kernelpatch.ko"));
            if !path.exists() {
                bail!(
                    "kernel module not found: {} (download it to this path first)",
                    path.display()
                );
            }
            path
        }
    };

    // Skip if the module is already loaded (the manager restart re-triggers the
    // app-zygote, which would otherwise fail with EEXIST on a second load).
    if Path::new("/sys/module/kernelpatch").exists() {
        info!("kernelpatch module already loaded, skip loading");
    } else {
        // Load the module without version check.
        insmod::insmod(&module, &[])?;
    }

    // Apply Magisk sepolicy live so the loaded module can work.
    sepolicy::apply_magisk_policy_live()?;

    // Mark jailbreak mode active.
    let _ = std::fs::create_dir_all("/data/adb/ap");
    let _ = std::fs::write("/data/adb/ap/jailbreak", "");

    println!("late-load complete: {}", module.display());

    // Restart the manager so it gets a fresh supercall fd from the newly loaded
    // module and reflects the jailbroken (rooted) state.
    if let Some(pkg) = package_name {
        info!("restarting manager {pkg}...");
        let _ = Command::new("am").args(["force-stop", &pkg]).status();
        let _ = Command::new("am")
            .args(["start", "-n", &format!("{pkg}/com.home.ten.ui.MainActivity")])
            .status();
    }

    // The jailbreak needs permissive SELinux to run, but once the module, policy
    // and manager are all in place, restore enforcing.
    if let Err(e) = std::fs::write("/sys/fs/selinux/enforce", "1") {
        warn!("failed to set SELinux enforcing: {e}");
    } else {
        info!("SELinux set to enforcing");
    }

    Ok(())
}
