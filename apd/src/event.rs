use anyhow::{bail, Context, Result};
use log::{info, warn};
use std::env;
use std::{collections::HashMap, path::Path};

use crate::module::prune_modules;
use crate::{
    assets, defs, mount,
    package::synchronize_package_uid,
    restorecon,
    supercall::{init_load_su_path, init_load_su_uid},
    utils::{self, ensure_clean_dir},
};

fn mount_partition(partition_name: &str, lowerdir: &Vec<String>) -> Result<()> {
    if lowerdir.is_empty() {
        warn!("partition: {partition_name} lowerdir is empty");
        return Ok(());
    }

    let partition = format!("/{partition_name}");

    // if /partition is a symlink and linked to /system/partition, then we don't need to overlay it separately
    if Path::new(&partition).read_link().is_ok() {
        warn!("partition: {partition} is a symlink");
        return Ok(());
    }

    let mut workdir = None;
    let mut upperdir = None;
    let system_rw_dir = Path::new(defs::SYSTEM_RW_DIR);
    if system_rw_dir.exists() {
        workdir = Some(system_rw_dir.join(partition_name).join("workdir"));
        upperdir = Some(system_rw_dir.join(partition_name).join("upperdir"));
    }

    mount::mount_overlay(&partition, lowerdir, workdir, upperdir)
}

pub fn mount_systemlessly(module_dir: &str) -> Result<()> {
    // construct overlay mount params
    let dir = std::fs::read_dir(module_dir);
    let Ok(dir) = dir else {
        bail!("open {} failed", defs::MODULE_DIR);
    };

    let mut system_lowerdir: Vec<String> = Vec::new();

    let partition = vec!["vendor", "product", "system_ext", "odm", "oem"];
    let mut partition_lowerdir: HashMap<String, Vec<String>> = HashMap::new();
    for ele in &partition {
        partition_lowerdir.insert((*ele).to_string(), Vec::new());
    }

    for entry in dir.flatten() {
        let module = entry.path();
        if !module.is_dir() {
            continue;
        }
        let disabled = module.join(defs::DISABLE_FILE_NAME).exists();
        if disabled {
            info!("module: {} is disabled, ignore!", module.display());
            continue;
        }
        let skip_mount = module.join(defs::SKIP_MOUNT_FILE_NAME).exists();
        if skip_mount {
            info!("module: {} skip_mount exist, skip!", module.display());
            continue;
        }

        let module_system = Path::new(&module).join("system");
        if module_system.is_dir() {
            system_lowerdir.push(format!("{}", module_system.display()));
        }

        for part in &partition {
            // if /partition is a mountpoint, we would move it to $MODPATH/$partition when install
            // otherwise it must be a symlink and we don't need to overlay!
            let part_path = Path::new(&module).join(part);
            if part_path.is_dir() {
                if let Some(v) = partition_lowerdir.get_mut(*part) {
                    v.push(format!("{}", part_path.display()));
                }
            }
        }
    }

    // mount /system first
    if let Err(e) = mount_partition("system", &system_lowerdir) {
        warn!("mount system failed: {:#}", e);
    }

    // mount other partitions
    for (k, v) in partition_lowerdir {
        if let Err(e) = mount_partition(&k, &v) {
            warn!("mount {k} failed: {:#}", e);
        }
    }

    Ok(())
}

pub fn on_post_data_fs(superkey: Option<String>) -> Result<()> {
    utils::umask(0);

    #[cfg(unix)]
    let _ = catch_bootlog();

    init_load_su_uid(&superkey);

    init_load_su_path(&superkey);

    if utils::has_magisk() {
        warn!("Magisk detected, skip post-fs-data!");
        return Ok(());
    }

    let key = "KERNELPATCH_VERSION";
    match env::var(key) {
        Ok(value) => println!("{}: {}", key, value),
        Err(_) => println!("{} not found", key),
    }

    let key = "KERNEL_VERSION";
    match env::var(key) {
        Ok(value) => println!("{}: {}", key, value),
        Err(_) => println!("{} not found", key),
    }

    let safe_mode = crate::utils::is_safe_mode(superkey.clone());

    if safe_mode {
        // we should still mount modules.img to `/data/adb/modules` in safe mode
        // becuase we may need to operate the module dir in safe mode
        warn!("safe mode, skip common post-fs-data.d scripts");
        if let Err(e) = crate::module::disable_all_modules() {
            warn!("disable all modules failed: {}", e);
        }
    } else {
        // Then exec common post-fs-data scripts
        if let Err(e) = crate::module::exec_common_scripts("post-fs-data.d", true) {
            warn!("exec common post-fs-data scripts failed: {}", e);
        }
    }

    let module_update_img = defs::MODULE_UPDATE_IMG;
    let module_img = defs::MODULE_IMG;
    let module_dir = defs::MODULE_DIR;
    let module_update_flag = Path::new(defs::WORKING_DIR).join(defs::UPDATE_FILE_NAME);

    // modules.img is the default image
    let mut target_update_img = &module_img;

    // we should clean the module mount point if it exists
    ensure_clean_dir(module_dir)?;

    assets::ensure_binaries().with_context(|| "binary missing")?;

    if Path::new(module_update_img).exists() {
        if module_update_flag.exists() {
            // if modules_update.img exists, and the the flag indicate this is an update
            // this make sure that if the update failed, we will fallback to the old image
            // if we boot succeed, we will rename the modules_update.img to modules.img #on_boot_complete
            target_update_img = &module_update_img;
            // And we should delete the flag immediately
            std::fs::remove_file(module_update_flag)?;
        } else {
            // if modules_update.img exists, but the flag not exist, we should delete it
            std::fs::remove_file(module_update_img)?;
        }
    }

    if !Path::new(target_update_img).exists() {
        return Ok(());
    }

    // we should always mount the module.img to module dir
    // becuase we may need to operate the module dir in safe mode
    info!("mount module image: {target_update_img} to {module_dir}");
    mount::AutoMountExt4::try_new(target_update_img, module_dir, false)
        .with_context(|| "mount module image failed".to_string())?;

    // if we are in safe mode, we should disable all modules
    if safe_mode {
        warn!("safe mode, skip post-fs-data scripts and disable all modules!");
        if let Err(e) = crate::module::disable_all_modules() {
            warn!("disable all modules failed: {}", e);
        }
        return Ok(());
    }

    if let Err(e) = prune_modules() {
        warn!("prune modules failed: {}", e);
    }

    if let Err(e) = restorecon::restorecon() {
        warn!("restorecon failed: {}", e);
    }

    // load sepolicy.rule
    if crate::module::load_sepolicy_rule().is_err() {
        warn!("load sepolicy.rule failed");
    }

    if let Err(e) = mount::mount_tmpfs(utils::get_tmp_path()) {
        warn!("do temp dir mount failed: {}", e);
    }

    // exec modules post-fs-data scripts
    // TODO: Add timeout
    if let Err(e) = crate::module::exec_stage_script("post-fs-data", true) {
        warn!("exec post-fs-data scripts failed: {}", e);
    }

    // load system.prop
    if let Err(e) = crate::module::load_system_prop() {
        warn!("load system.prop failed: {}", e);
    }

    // mount module systemlessly by overlay
    if let Err(e) = mount_systemlessly(module_dir) {
        warn!("do systemless mount failed: {}", e);
    }

    run_stage("post-mount", superkey, true);

    std::env::set_current_dir("/").with_context(|| "failed to chdir to /")?;

    Ok(())
}

fn run_stage(stage: &str, superkey: Option<String>, block: bool) {
    utils::umask(0);

    if utils::has_magisk() {
        warn!("Magisk detected, skip {stage}");
        return;
    }

    if crate::utils::is_safe_mode(superkey) {
        warn!("safe mode, skip {stage} scripts");
        if let Err(e) = crate::module::disable_all_modules() {
            warn!("disable all modules failed: {}", e);
        }
        return;
    }

    if let Err(e) = crate::module::exec_common_scripts(&format!("{stage}.d"), block) {
        warn!("Failed to exec common {stage} scripts: {e}");
    }
    if let Err(e) = crate::module::exec_stage_script(stage, block) {
        warn!("Failed to exec {stage} scripts: {e}");
    }
}

pub fn on_services(superkey: Option<String>) -> Result<()> {
    info!("on_services triggered!");
    run_stage("service", superkey, false);

    Ok(())
}

pub fn on_boot_completed(superkey: Option<String>) -> Result<()> {
    info!("on_boot_completed triggered!");
    let module_update_img = Path::new(defs::MODULE_UPDATE_IMG);
    let module_img = Path::new(defs::MODULE_IMG);
    if module_update_img.exists() {
        // this is a update and we successfully booted
        if std::fs::rename(module_update_img, module_img).is_err() {
            warn!("Failed to rename images, copy it now.",);
            std::fs::copy(module_update_img, module_img)
                .with_context(|| "Failed to copy images")?;
            std::fs::remove_file(module_update_img).with_context(|| "Failed to remove image!")?;
        }
    }

    //synchronize_package_uid();
    run_stage("boot-completed", superkey, false);

    Ok(())
}

pub fn on_sync_uid() -> Result<()> {
    synchronize_package_uid();
    return Ok(());
}

#[cfg(unix)]
fn catch_bootlog() -> Result<()> {
    use std::os::unix::process::CommandExt;
    use std::process::Stdio;

    let logdir = Path::new(defs::LOG_DIR);
    utils::ensure_dir_exists(logdir)?;
    let aptchlog = logdir.join("apatch.log");
    let oldapatchlog = logdir.join("apatch.old.log");

    if aptchlog.exists() {
        std::fs::rename(&aptchlog, oldapatchlog)?;
    }

    let aptchlog = std::fs::File::create(aptchlog)?;

    // timeout -s 9 30s logcat > apatch.log
    let result = unsafe {
        std::process::Command::new("timeout")
            .process_group(0)
            .pre_exec(|| {
                utils::switch_cgroups();
                Ok(())
            })
            .arg("-s")
            .arg("9")
            .arg("30s")
            .arg("logcat")
            .stdout(Stdio::from(aptchlog))
            .spawn()
    };

    if let Err(e) = result {
        warn!("Failed to start logcat: {:#}", e);
    }

    Ok(())
}
