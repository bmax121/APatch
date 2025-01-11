use crate::module;
use crate::supercall::fork_for_result;
use crate::utils::{switch_cgroups,ensure_dir_exists,get_work_dir,ensure_file_exists};
use crate::{
    assets, defs, mount, restorecon, supercall,
    supercall::{init_load_package_uid_config, init_load_su_path, refresh_ap_package_list},
    utils::{self, ensure_clean_dir},
};
use anyhow::{bail, ensure, Context, Result};
use log::{info, warn};
use crate::m_mount;
use notify::event::{ModifyKind, RenameMode};
use notify::{Config, Event, EventKind, INotifyWatcher, RecursiveMode, Watcher};
use std::ffi::CStr;
use std::os::unix::fs::PermissionsExt;
use std::os::unix::process::CommandExt;
use std::path::{PathBuf,Path};
use std::process::Command;
use std::sync::{Arc, Mutex};
use std::time::Duration;
use std::{collections::HashMap, thread};
use std::{env, fs,io};
use rustix::{fd::AsFd, fs::CWD, mount::*};
use std::fs::{remove_dir_all, rename};
use walkdir::WalkDir;
use extattr::{lsetxattr, lgetxattr, Flags as XattrFlags};

fn copy_with_xattr(src: &Path, dest: &Path) -> io::Result<()> {
    fs::copy(src, dest)?;

    if let Ok(xattr_value) = lgetxattr(src, "security.selinux") {
        lsetxattr(dest, "security.selinux", &xattr_value, XattrFlags::empty())?;
    }

    Ok(())
}

fn copy_dir_with_xattr(src: &Path, dest: &Path) -> io::Result<()> {
    for entry in WalkDir::new(src) {
        let entry = entry?;
        let rel_path = entry
            .path()
            .strip_prefix(src)
            .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))?;
        let target_path = dest.join(rel_path);
        if entry.file_type().is_dir() {
            fs::create_dir_all(&target_path)?;
        } else if entry.file_type().is_file() {
            copy_with_xattr(entry.path(), &target_path)?;
        }
    }
    Ok(())
}

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

pub fn mount_systemlessly(module_dir: &str,is_img: bool) -> Result<()> {
    // construct overlay mount params
    if !is_img {

        info!("fallback to modules.img");
        let module_update_dir = defs::MODULE_DIR;
        let module_dir = defs::MODULE_MOUNT_DIR;
        let tmp_module_img = defs::MODULE_UPDATE_TMP_IMG; 
        let tmp_module_path = Path::new(tmp_module_img);

        ensure_clean_dir(module_dir)?;
        info!("- Preparing image");
        if tmp_module_path.exists() { //if it have update,remove tmp file
            std::fs::remove_file(tmp_module_path)?;
        }
        let total_size = calculate_total_size(Path::new(module_update_dir))?; //create modules adapt size
        info!("Total size of files in '{}': {} bytes",tmp_module_path.display(),total_size);
        let grow_size =  128 * 1024 * 1024 + total_size;
        fs::File::create(tmp_module_img)
            .context("Failed to create ext4 image file")?
            .set_len(grow_size)
            .context("Failed to extend ext4 image")?;
        let result = Command::new("mkfs.ext4")
            .arg("-b")
            .arg("1024")
            .arg(tmp_module_img)
            .stdout(std::process::Stdio::piped())
            .output()?;
        ensure!(result.status.success(),"Failed to format ext4 image: {}",String::from_utf8(result.stderr).unwrap());
        info!("Checking Image");
        module::check_image(tmp_module_img)?;
        info!("- Mounting image");
        mount::AutoMountExt4::try_new(tmp_module_img, module_dir, false)
            .with_context(|| "mount module image failed".to_string())?;
        info!("mounted {} to {}", tmp_module_img, module_dir);
        let _ = restorecon::setsyscon(module_dir);
        let command_string = format!("cp --preserve=context -R {}* {};",module_update_dir,module_dir);
        let args = vec!["-c",&command_string];
        let _ = utils::run_command("sh", &args, None)?.wait()?;
        mount_systemlessly(module_dir,true)?;
        return Ok(());
    }
    let dir = fs::read_dir(module_dir);
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
        //ensure_file_exists(format!("{}",defs::BIND_MOUNT_FILE))?;
        //ensure_clean_dir(defs::MODULE_DIR)?;
        //info!("bind_mount enable,overlayfs is not work,clear module_dir");
        
        
    }

    // mount other partitions
    for (k, v) in partition_lowerdir {
        if let Err(e) = mount_partition(&k, &v) {
            warn!("mount {k} failed: {:#}", e);
        }
    }

    Ok(())
}

pub fn systemless_bind_mount(module_dir: &str) -> Result<()> {
    //let propagation_flags = MountPropagationFlags::PRIVATE;

    //let combined_flags = MountFlags::empty() | MountFlags::from_bits_truncate(propagation_flags.bits());
    // set tmp_path prvate
    //mount("tmpfs",utils::get_tmp_path(),"tmpfs",combined_flags,"")?;
 
    // construct bind mount params
    m_mount::magic_mount()?;
    Ok(())
}

pub fn calculate_total_size(path: &Path) -> std::io::Result<u64> {
    let mut total_size = 0;
    if path.is_dir() {
        for entry in fs::read_dir(path)? {
            let entry = entry?;
            let file_type = entry.file_type()?;
            if file_type.is_file() {
                total_size += entry.metadata()?.len();
            } else if file_type.is_dir() {
                total_size += calculate_total_size(&entry.path())?;
            }
        }
    }
    Ok(total_size)
}
pub fn move_file(module_update_dir: &str,module_dir: &str)-> Result<()> {
    for entry in fs::read_dir(module_update_dir)? {
        let entry = entry?;
        let file_name = entry.file_name();
        let file_name_str = file_name.to_string_lossy();

        if entry.path().is_dir() {
            let source_path = Path::new(module_update_dir).join(file_name_str.as_ref()); 
            let target_path = Path::new(module_dir).join(file_name_str.as_ref()); 
            if target_path.exists() {
                info!("Removing existing folder in target directory: {}", file_name_str);
                remove_dir_all(&target_path)?;  
            }

            info!("Moving {} to target directory", file_name_str);
            rename(&source_path, &target_path)?;  
        }
    }
    return Ok(());
}
pub fn on_post_data_fs(superkey: Option<String>) -> Result<()> {
    utils::umask(0);
    use std::process::Stdio;
    #[cfg(unix)]
    init_load_package_uid_config(&superkey);

    init_load_su_path(&superkey);

    let args = ["/data/adb/ap/bin/magiskpolicy", "--magisk", "--live"];
    fork_for_result("/data/adb/ap/bin/magiskpolicy", &args, &superkey);

    info!("Re-privilege apd profile after injecting sepolicy");
    supercall::privilege_apd_profile(&superkey);

    if utils::has_magisk() {
        warn!("Magisk detected, skip post-fs-data!");
        return Ok(());
    }

    // Create log environment
    if !Path::new(defs::APATCH_LOG_FOLDER).exists() {
        fs::create_dir(defs::APATCH_LOG_FOLDER).expect("Failed to create log folder");
        let permissions = fs::Permissions::from_mode(0o700);
        fs::set_permissions(defs::APATCH_LOG_FOLDER, permissions)
            .expect("Failed to set permissions");
    }
    let mut command_string = format!(
        "rm -rf {}*.old.log; for file in {}*; do mv \"$file\" \"$file.old.log\"; done",
        defs::APATCH_LOG_FOLDER,
        defs::APATCH_LOG_FOLDER
    );
    let mut args = vec!["-c", &command_string]; 
    // for all file to .old
    let result = utils::run_command("sh", &args, None)?.wait()?;
    if result.success() {
        info!("Successfully deleted .old files.");
    } else {
        info!("Failed to delete .old files.");
    }
    let logcat_path = format!("{}locat.log", defs::APATCH_LOG_FOLDER);
    let dmesg_path = format!("{}dmesg.log", defs::APATCH_LOG_FOLDER);
    let bootlog = std::fs::File::create(dmesg_path)?;
    args = vec!["-s","9","120s","logcat","-b","main,system,crash","-f",&logcat_path,"logcatcher-bootlog:S","&"];
    let _ = unsafe {
        std::process::Command::new("timeout")
            .process_group(0)
            .pre_exec(|| {
                utils::switch_cgroups();
                Ok(())
            })
            .args(args)
            .spawn()
    };
    args = vec!["-s","9","120s","dmesg","-w"];
    let result = unsafe {
        std::process::Command::new("timeout")
            .process_group(0)
            .pre_exec(|| {
                utils::switch_cgroups();
                Ok(())
            })
            .args(args)
            .stdout(Stdio::from(bootlog))
            .spawn()
    };

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

    let safe_mode = utils::is_safe_mode(superkey.clone());

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
    let module_update_dir = defs::MODULE_UPDATE_TMP_DIR; //save module place
    let module_dir = defs::MODULE_DIR;// run modules place
    let module_update_flag = Path::new(defs::WORKING_DIR).join(defs::UPDATE_FILE_NAME);// if update ,there will be renew modules file
    assets::ensure_binaries().with_context(|| "binary missing")?;

    let tmp_module_img = defs::MODULE_UPDATE_TMP_IMG; 
    let tmp_module_path = Path::new(tmp_module_img);
    move_file(module_update_dir,module_dir)?;
    info!("remove update flag");
    let _ = fs::remove_file(module_update_flag);
    if tmp_module_path.exists() { //if it have update,remove tmp file
        std::fs::remove_file(tmp_module_path)?;
    }

    let lite_file = Path::new(defs::LITEMODE_FILE);

    
    if safe_mode {
        warn!("safe mode, skip post-fs-data scripts and disable all modules!");
        if let Err(e) = crate::module::disable_all_modules() {
            warn!("disable all modules failed: {}", e);
        }
        return Ok(());
    }

    if let Err(e) = module::prune_modules() {
        warn!("prune modules failed: {}", e);
    }

    if let Err(e) = restorecon::restorecon() {
        warn!("restorecon failed: {}", e);
    }

    // load sepolicy.rule
    if crate::module::load_sepolicy_rule().is_err() {
        warn!("load sepolicy.rule failed");
    }
    if lite_file.exists() {
        info!("litemode runing skip mount tempfs")
    }else{
        if let Err(e) = mount::mount_tmpfs(utils::get_tmp_path()) {
            warn!("do temp dir mount failed: {}", e);
        }

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

    
    if lite_file.exists() {
        info!("litemode runing skip mount state")
    }else{

        if utils::should_enable_overlay()? {
            // mount module systemlessly by overlay
            let work_dir = get_work_dir();
            let tmp_dir = PathBuf::from(work_dir.clone());
            ensure_dir_exists(&tmp_dir)?;
            mount(defs::AP_OVERLAY_SOURCE, &tmp_dir, "tmpfs", MountFlags::empty(), "").context("mount tmp")?;
            mount_change(&tmp_dir, MountPropagationFlags::PRIVATE).context("make tmp private")?;
            let dir_names = vec!["vendor", "product", "system_ext", "odm", "oem", "system"];
            let dir = fs::read_dir(module_dir)?;
            for entry in dir.flatten() {
                let module_path = entry.path();
                let disabled = module_path.join(defs::DISABLE_FILE_NAME).exists();
                if disabled {
                    info!("module: {} is disabled, ignore!", module_path.display());
                    continue;
                }
                if module_path.is_dir() {
                    let module_name = module_path.file_name().unwrap().to_string_lossy();
                    let module_dest = Path::new(&work_dir).join(module_name.as_ref());
    
                    for sub_dir in dir_names.iter() {
                        let sub_dir_path = module_path.join(sub_dir);
                        if sub_dir_path.exists() && sub_dir_path.is_dir() {
                            let sub_dir_dest = module_dest.join(sub_dir);
                            fs::create_dir_all(&sub_dir_dest)?;
    
                            copy_dir_with_xattr(&sub_dir_path, &sub_dir_dest)?;
                        }
                    }
                }
            }
            if let Err(e) = mount_systemlessly(&get_work_dir(),false) {
                warn!("do systemless mount failed: {}", e);
            }
            if let Err(e) = unmount(&tmp_dir, UnmountFlags::DETACH) {
                log::error!("failed to unmount tmp {}", e);
            }
        }else{
            if let Err(e) = systemless_bind_mount(module_dir) {
                warn!("do systemless bind_mount failed: {}", e);
            }
            
    
        }
    }
    

    run_stage("post-mount", superkey, true);

    env::set_current_dir("/").with_context(|| "failed to chdir to /")?;

    Ok(())
}

fn run_stage(stage: &str, superkey: Option<String>, block: bool) {
    utils::umask(0);

    if utils::has_magisk() {
        warn!("Magisk detected, skip {stage}");
        return;
    }

    if utils::is_safe_mode(superkey) {
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

fn run_uid_monitor() {
    info!("Trigger run_uid_monitor!");

    let mut command = &mut Command::new("/data/adb/apd");
    {
        command = command.process_group(0);
        command = unsafe {
            command.pre_exec(|| {
                // ignore the error?
                switch_cgroups();
                Ok(())
            })
        };
    }
    command = command.arg("uid-listener");

    command
        .spawn()
        .map(|_| ())
        .expect("[run_uid_monitor] Failed to run uid monitor");
}

pub fn on_boot_completed(superkey: Option<String>) -> Result<()> {
    info!("on_boot_completed triggered!");

    run_uid_monitor();
    run_stage("boot-completed", superkey, false);

    Ok(())
}

pub fn start_uid_listener() -> Result<()> {
    info!("start_uid_listener triggered!");
    println!("[start_uid_listener] Registering...");

    // create inotify instance
    const SYS_PACKAGES_LIST_TMP: &str = "/data/system/packages.list.tmp";
    let sys_packages_list_tmp = PathBuf::from(&SYS_PACKAGES_LIST_TMP);
    let dir: PathBuf = sys_packages_list_tmp.parent().unwrap().into();

    let (tx, rx) = std::sync::mpsc::channel();
    let tx_clone = tx.clone();
    let mutex = Arc::new(Mutex::new(()));

    let mut watcher = INotifyWatcher::new(
        move |ev: notify::Result<Event>| match ev {
            Ok(Event {
                kind: EventKind::Modify(ModifyKind::Name(RenameMode::Both)),
                paths,
                ..
            }) => {
                if paths.contains(&sys_packages_list_tmp) {
                    info!("[uid_monitor] System packages list changed, sending to tx...");
                    tx_clone.send(false).unwrap()
                }
            }
            Err(err) => warn!("inotify error: {err}"),
            _ => (),
        },
        Config::default(),
    )?;

    watcher.watch(dir.as_ref(), RecursiveMode::NonRecursive)?;

    let mut debounce = false;
    while let Ok(delayed) = rx.recv() {
        if delayed {
            debounce = false;
            let skey = CStr::from_bytes_with_nul(b"su\0")
                .expect("[start_uid_listener] CStr::from_bytes_with_nul failed");
            refresh_ap_package_list(&skey, &mutex);
        } else if !debounce {
            thread::sleep(Duration::from_secs(1));
            debounce = true;
            tx.send(true).unwrap();
        }
    }

    Ok(())
}
