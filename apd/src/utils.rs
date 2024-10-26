use anyhow::{bail, Context, Error, Ok, Result};
use log::{info, warn};
use std::ffi::CString;
use std::{
    fs::{self,create_dir_all, File, OpenOptions},
    io::{BufRead, BufReader,ErrorKind::AlreadyExists, Write},
    path::Path,
    process::Stdio
};

use std::process::Command;
#[allow(unused_imports)]
use std::fs::{set_permissions, Permissions};
#[cfg(unix)]
use std::os::unix::prelude::PermissionsExt;
use crate::defs;
use std::fs::metadata;

use crate::supercall::sc_su_get_safemode;

pub fn ensure_clean_dir(dir: &str) -> Result<()> {
    let path = Path::new(dir);
    log::debug!("ensure_clean_dir: {}", path.display());
    if path.exists() {
        log::debug!("ensure_clean_dir: {} exists, remove it", path.display());
        std::fs::remove_dir_all(path)?;
    }
    Ok(std::fs::create_dir_all(path)?)
}

pub fn ensure_file_exists<T: AsRef<Path>>(file: T) -> Result<()> {
    match File::options().write(true).create_new(true).open(&file) {
        std::result::Result::Ok(_) => Ok(()),
        Err(err) => {
            if err.kind() == AlreadyExists && file.as_ref().is_file() {
                Ok(())
            } else {
                Err(Error::from(err))
                    .with_context(|| format!("{} is not a regular file", file.as_ref().display()))
            }
        }
    }
}

pub fn ensure_dir_exists<T: AsRef<Path>>(dir: T) -> Result<()> {
    let result = create_dir_all(&dir).map_err(Error::from);
    if dir.as_ref().is_dir() {
        result
    } else if result.is_ok() {
        bail!("{} is not a regular directory", dir.as_ref().display())
    } else {
        result
    }
}

// todo: ensure
pub fn ensure_binary<T: AsRef<Path>>(path: T) -> Result<()> {
    set_permissions(&path, Permissions::from_mode(0o755))?;
    Ok(())
}

#[cfg(any(target_os = "linux", target_os = "android"))]
pub fn getprop(prop: &str) -> Option<String> {
    android_properties::getprop(prop).value()
}

#[cfg(not(any(target_os = "linux", target_os = "android")))]
pub fn getprop(_prop: &str) -> Option<String> {
    unimplemented!()
}
pub fn run_command(command: &str, args: &[&str], stdout: Option<Stdio>) -> anyhow::Result<std::process::Child> {
    let mut command_builder = Command::new(command);
    command_builder.args(args);
    if let Some(out) = stdout {
        command_builder.stdout(out);
    }
    let child = command_builder.spawn()?;  
    Ok(child) 
}
pub fn is_safe_mode(superkey: Option<String>) -> bool {
    let safemode = getprop("persist.sys.safemode")
        .filter(|prop| prop == "1")
        .is_some()
        || getprop("ro.sys.safemode")
            .filter(|prop| prop == "1")
            .is_some();
    info!("safemode: {}", safemode);
    if safemode {
        return true;
    }
    let safemode = superkey
        .as_ref()
        .and_then(|key_str| CString::new(key_str.as_str()).ok())
        .map_or_else(
            || {
                warn!("[is_safe_mode] No valid superkey provided, assuming safemode as false.");
                false
            },
            |cstr| sc_su_get_safemode(&cstr) == 1,
        );
    info!("kernel_safemode: {}", safemode);
    safemode
}


#[cfg(any(target_os = "linux", target_os = "android"))]
pub fn switch_mnt_ns(pid: i32) -> Result<()> {
    use anyhow::ensure;
    use std::os::fd::AsRawFd;
    let path = format!("/proc/{pid}/ns/mnt");
    let fd = std::fs::File::open(path)?;
    let current_dir = std::env::current_dir();
    let ret = unsafe { libc::setns(fd.as_raw_fd(), libc::CLONE_NEWNS) };
    if let std::result::Result::Ok(current_dir) = current_dir {
        let _ = std::env::set_current_dir(current_dir);
    }
    ensure!(ret == 0, "switch mnt ns failed");
    Ok(())
}

pub fn is_overlayfs_supported() -> Result<bool> {
    let file = File::open("/proc/filesystems")
        .with_context(|| "Failed to open /proc/filesystems")?;
    let reader = BufReader::new(file);

    let overlay_supported = reader.lines().any(|line| {
        if let std::result::Result::Ok(line) = line {
            line.contains("overlay")
        } else {
            false
        }
    });

    Ok(overlay_supported)
}
pub fn is_symlink(path: &str) -> bool {
    match fs::symlink_metadata(path) {
        std::result::Result::Ok(metadata) => metadata.file_type().is_symlink(),
        std::result::Result::Err(_) => false, 
    }
}
pub fn should_enable_overlay() -> Result<bool> {
    let bind_mount_exists = Path::new(defs::BIND_MOUNT_FILE).exists();
    let overlay_supported = is_overlayfs_supported()?;

    Ok(!bind_mount_exists && overlay_supported)
}

#[cfg(any(target_os = "linux", target_os = "android"))]
pub fn unshare_mnt_ns() -> Result<()> {
    use anyhow::ensure;
    let ret = unsafe { libc::unshare(libc::CLONE_NEWNS) };
    ensure!(ret == 0, "unshare mnt ns failed");
    Ok(())
}

fn switch_cgroup(grp: &str, pid: u32) {
    let path = Path::new(grp).join("cgroup.procs");
    if !path.exists() {
        return;
    }

    let fp = OpenOptions::new().append(true).open(path);
    if let std::result::Result::Ok(mut fp) = fp {
        let _ = writeln!(fp, "{pid}");
    }
}

pub fn switch_cgroups() {
    let pid = std::process::id();
    switch_cgroup("/acct", pid);
    switch_cgroup("/dev/cg2_bpf", pid);
    switch_cgroup("/sys/fs/cgroup", pid);

    if getprop("ro.config.per_app_memcg")
        .filter(|prop| prop == "false")
        .is_none()
    {
        switch_cgroup("/dev/memcg/apps", pid);
    }
}

#[cfg(any(target_os = "linux", target_os = "android"))]
pub fn umask(mask: u32) {
    unsafe { libc::umask(mask) };
}

#[cfg(not(any(target_os = "linux", target_os = "android")))]
pub fn umask(_mask: u32) {
    unimplemented!("umask is not supported on this platform")
}

pub fn has_magisk() -> bool {
    which::which("magisk").is_ok()
}
pub fn get_tmp_path() -> &'static str {
    if metadata(defs::TEMP_DIR_LEGACY).is_ok() {
        return defs::TEMP_DIR_LEGACY;
    }
    if metadata(defs::TEMP_DIR).is_ok() {
        return defs::TEMP_DIR;
    }
    ""
}
