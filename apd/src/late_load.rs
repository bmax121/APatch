//! Jailbreak mode late-load: load a KernelPatch kernel module on a stock kernel
//! and apply Magisk SELinux policy, mirroring KernelSU's `late-load` flow.

use anyhow::{Context, Result, bail};
use log::{info, warn};
use regex_lite::Regex;
use std::ffi::{CStr, CString};
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::process::Command;

use crate::{insmod, sepolicy, utils};

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

const DEFAULT_PACKAGE: &str = "me.bmax.apatch";

/// Locate the manager APK by scanning `/data/app` directly, without going
/// through the `package` service (binder/`pm` can be unreachable this early:
/// SELinux is still enforcing and Magisk policy has not been applied yet).
/// Layout: `/data/app/~~<random>/<package>-<random>/base.apk`.
fn apk_path(package: &str) -> Result<PathBuf> {
    let prefix = format!("{package}-");
    for root in ["/data/app", "/data/app/*"] {
        for entry in glob_dirs(root)? {
            let Some(name) = entry.file_name().and_then(|n| n.to_str()) else {
                continue;
            };
            if name == package || name.starts_with(&prefix) {
                let apk = entry.join("base.apk");
                if apk.is_file() {
                    return Ok(apk);
                }
            }
        }
    }
    bail!("cannot find the APK of package {package} under /data/app")
}

/// List the subdirectories of `dir`, or of each immediate subdirectory of
/// `dir` when `dir` ends with `/*` (one extra level, for the `~~<random>/`
/// wrapper directories Android creates under `/data/app`).
fn glob_dirs(dir: &str) -> Result<Vec<PathBuf>> {
    let Some(base) = dir.strip_suffix("/*") else {
        return Ok(read_dirs(Path::new(dir)));
    };
    let mut dirs = Vec::new();
    for sub in read_dirs(Path::new(base)) {
        dirs.extend(read_dirs(&sub));
    }
    Ok(dirs)
}

fn read_dirs(dir: &Path) -> Vec<PathBuf> {
    let Ok(entries) = fs::read_dir(dir) else {
        return Vec::new();
    };
    entries
        .filter_map(|e| e.ok())
        .map(|e| e.path())
        .filter(|p| p.is_dir())
        .collect()
}

/// Read `assets/<name>` from the manager APK into memory.
fn read_from_assets(package: &str, name: &str) -> Result<Vec<u8>> {
    let apk = apk_path(package)?;
    let mut archive = zip::ZipArchive::new(File::open(&apk)?)?;
    let mut entry = archive
        .by_name(&format!("assets/{name}"))
        .with_context(|| format!("{name} not found in {}", apk.display()))?;
    let mut data = Vec::new();
    entry.read_to_end(&mut data)?;
    Ok(data)
}

/// `apd late-load [--module <path>] [--kmi <androidX-Y.Z>] [--package-name <pkg>]`
///
/// Loads the KernelPatch module for the device's kernel (auto-detecting the KMI
/// and looking for `/data/adb/ap/{kmi}_kernelpatch.ko` when no module is given,
/// loading straight from the `assets` folder of the manager APK when it is missing),
/// then applies Magisk SELinux policy live and restarts the manager app so it
/// re-initializes with the freshly granted root. Module loading (mount and the
/// `post-fs-data`/`post-mount`/`service`/`boot-completed` stages) is NOT done
/// here; it is left to `apd soft-reboot`.
pub fn run(
    module: Option<PathBuf>,
    kmi: Option<String>,
    package_name: Option<String>,
) -> Result<()> {
    // Detach from the (adb/app) shell first: module stage scripts may outlive it,
    // and adbd is restarted at the end of a magica jailbreak.
    utils::daemonize()?;

    let kmi = kmi.or_else(detect_kmi);
    let name = module.as_ref().map(|m| m.display().to_string());

    // Skip if the module is already loaded (the manager restart re-triggers the
    // app-zygote, which would otherwise fail with EEXIST on a second load).
    if Path::new("/sys/module/kernelpatch").exists() {
        info!("kernelpatch module already loaded, skip loading");
    } else {
        match module {
            Some(path) => insmod::insmod(&path, &[])?,
            None => {
                let Some(kmi) = kmi else {
                    bail!("cannot detect kernel KMI (androidX-Y.Z)");
                };
                info!("detected KMI: {kmi}");
                let file = format!("{kmi}_kernelpatch.ko");
                let path = PathBuf::from(format!("/data/adb/ap/{file}"));
                if path.exists() {
                    insmod::insmod(&path, &[])?;
                } else {
                    info!("{} not found, loading {file} from APK assets", path.display());
                    let package = package_name.as_deref().unwrap_or(DEFAULT_PACKAGE);
                    let data = read_from_assets(package, &file)?;
                    insmod::load_module(&data, &CString::default())?;
                }
            }
        }
    }

    // Apply Magisk sepolicy live so the loaded module can work.
    sepolicy::apply_magisk_policy_live()?;

    // Mark jailbreak mode active.
    let _ = std::fs::create_dir_all("/data/adb/ap");
    let _ = std::fs::write("/data/adb/ap/jailbreak", "");

    println!("late-load complete: {}", name.as_deref().unwrap_or("kernelpatch"));

    // Restart the manager so it gets a fresh supercall fd from the newly loaded
    // module and reflects the jailbroken (rooted) state.
    if let Some(pkg) = package_name {
        info!("restarting manager {pkg}...");
        let _ = Command::new("am").args(["force-stop", &pkg]).status();
        let _ = Command::new("am")
            .args([
                "start",
                "-n",
                &format!("{pkg}/me.bmax.apatch.ui.MainActivity"),
            ])
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
