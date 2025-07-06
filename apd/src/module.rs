#[allow(clippy::wildcard_imports)]
use crate::utils::*;
use crate::{assets, defs, restorecon};
use anyhow::{Context, Result, anyhow, bail, ensure};
use const_format::concatcp;
use is_executable::is_executable;
use java_properties::PropertiesIter;
use log::{info, warn};
use std::{
    collections::HashMap,
    env::var as env_var,
    fs,
    io::Cursor,
    path::{Path, PathBuf},
    process::{Command, Stdio},
    str::FromStr,
};
use zip_extensions::zip_extract_file_to_memory;

#[cfg(unix)]
use std::os::unix::{prelude::PermissionsExt, process::CommandExt};

const INSTALLER_CONTENT: &str = include_str!("./installer.sh");
const INSTALLER_CONTENT_: &str = include_str!("./installer_bind.sh");
const INSTALL_MODULE_SCRIPT: &str = concatcp!(
    INSTALLER_CONTENT,
    "\n",
    "install_module",
    "\n",
    "exit 0",
    "\n"
);
const INSTALL_MODULE_SCRIPT_: &str = concatcp!(
    INSTALLER_CONTENT_,
    "\n",
    "install_module",
    "\n",
    "exit 0",
    "\n"
);

fn exec_install_script(module_file: &str) -> Result<()> {
    let realpath =
        fs::canonicalize(module_file).with_context(|| format!("realpath: {module_file} failed"))?;

    let content;

    if !should_enable_overlay()? {
        content = INSTALL_MODULE_SCRIPT_.to_string();
    } else {
        content = INSTALL_MODULE_SCRIPT.to_string();
    }
    let result = Command::new(assets::BUSYBOX_PATH)
        .args(["sh", "-c", &content])
        .env("ASH_STANDALONE", "1")
        .env(
            "PATH",
            format!(
                "{}:{}",
                env_var("PATH").unwrap(),
                defs::BINARY_DIR.trim_end_matches('/')
            ),
        )
        .env("APATCH", "true")
        .env("APATCH_VER", defs::VERSION_NAME)
        .env("APATCH_VER_CODE", defs::VERSION_CODE)
        .env(
            "APATCH_BIND_MOUNT",
            format!("{}", !should_enable_overlay()?),
        )
        .env("OUTFD", "1")
        .env("ZIPFILE", realpath)
        .status()?;
    ensure!(result.success(), "Failed to install module script");
    Ok(())
}

// becuase we use something like A-B update
// we need to update the module state after the boot_completed
// if someone(such as the module) install a module before the boot_completed
// then it may cause some problems, just forbid it
fn ensure_boot_completed() -> Result<()> {
    // ensure getprop sys.boot_completed == 1
    if getprop("sys.boot_completed").as_deref() != Some("1") {
        bail!("Android is Booting!");
    }
    Ok(())
}

fn mark_update() -> Result<()> {
    ensure_file_exists(concatcp!(defs::WORKING_DIR, defs::UPDATE_FILE_NAME))
}

fn mark_module_state(module: &str, flag_file: &str, create_or_delete: bool) -> Result<()> {
    let module_state_file = Path::new(defs::MODULE_DIR).join(module).join(flag_file);
    if create_or_delete {
        ensure_file_exists(module_state_file)
    } else {
        if module_state_file.exists() {
            fs::remove_file(module_state_file)?;
        }
        Ok(())
    }
}

fn foreach_module(active_only: bool, mut f: impl FnMut(&Path) -> Result<()>) -> Result<()> {
    let modules_dir = Path::new(defs::MODULE_DIR);
    let dir = fs::read_dir(modules_dir)?;
    for entry in dir.flatten() {
        let path = entry.path();
        if !path.is_dir() {
            warn!("{} is not a directory, skip", path.display());
            continue;
        }

        if active_only && path.join(defs::DISABLE_FILE_NAME).exists() {
            info!("{} is disabled, skip", path.display());
            continue;
        }
        if active_only && path.join(defs::REMOVE_FILE_NAME).exists() {
            warn!("{} is removed, skip", path.display());
            continue;
        }

        f(&path)?;
    }

    Ok(())
}

fn foreach_active_module(f: impl FnMut(&Path) -> Result<()>) -> Result<()> {
    foreach_module(true, f)
}

pub fn check_image(img: &str) -> Result<()> {
    let result = Command::new("e2fsck")
        .args(["-yf", img])
        .stdout(Stdio::piped())
        .status()
        .with_context(|| format!("Failed to exec e2fsck {img}"))?;
    let code = result.code();
    // 0 or 1 is ok
    // 0: no error
    // 1: file system errors corrected
    // https://man7.org/linux/man-pages/man8/e2fsck.8.html
    // ensure!(
    //     code == Some(0) || code == Some(1),
    //     "Failed to check image, e2fsck exit code: {}",
    //     code.unwrap_or(-1)
    // );
    info!("e2fsck exit code: {}", code.unwrap_or(-1));
    Ok(())
}

pub fn load_sepolicy_rule() -> Result<()> {
    foreach_active_module(|path| {
        let rule_file = path.join("sepolicy.rule");
        if !rule_file.exists() {
            return Ok(());
        }

        info!("load policy: {}", &rule_file.display());
        Command::new(assets::MAGISKPOLICY_PATH)
            .arg("--live")
            .arg("--apply")
            .arg(&rule_file)
            .status()
            .with_context(|| format!("Failed to exec {}", rule_file.display()))?;
        Ok(())
    })?;

    Ok(())
}

fn exec_script<T: AsRef<Path>>(path: T, wait: bool) -> Result<()> {
    info!("exec {}", path.as_ref().display());

    let mut command = &mut Command::new(assets::BUSYBOX_PATH);
    #[cfg(unix)]
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
    command = command
        .current_dir(path.as_ref().parent().unwrap())
        .arg("sh")
        .arg(path.as_ref())
        .env("ASH_STANDALONE", "1")
        .env("APATCH", "true")
        .env("APATCH_VER", defs::VERSION_NAME)
        .env("APATCH_VER_CODE", defs::VERSION_CODE)
        .env(
            "APATCH_BIND_MOUNT",
            format!("{}", !should_enable_overlay()?),
        )
        .env(
            "PATH",
            format!(
                "{}:{}",
                env_var("PATH")?,
                defs::BINARY_DIR.trim_end_matches('/')
            ),
        );

    let result = if wait {
        command.status().map(|_| ())
    } else {
        command.spawn().map(|_| ())
    };
    result.map_err(|err| anyhow!("Failed to exec {}: {}", path.as_ref().display(), err))
}

pub fn exec_stage_script(stage: &str, block: bool) -> Result<()> {
    foreach_active_module(|module| {
        let script_path = module.join(format!("{stage}.sh"));
        if !script_path.exists() {
            return Ok(());
        }

        exec_script(&script_path, block)
    })?;

    Ok(())
}

pub fn exec_common_scripts(dir: &str, wait: bool) -> Result<()> {
    let script_dir = Path::new(defs::ADB_DIR).join(dir);
    if !script_dir.exists() {
        info!("{} not exists, skip", script_dir.display());
        return Ok(());
    }

    let dir = fs::read_dir(&script_dir)?;
    for entry in dir.flatten() {
        let path = entry.path();

        if !is_executable(&path) {
            warn!("{} is not executable, skip", path.display());
            continue;
        }

        exec_script(path, wait)?;
    }

    Ok(())
}

pub fn load_system_prop() -> Result<()> {
    foreach_active_module(|module| {
        let system_prop = module.join("system.prop");
        if !system_prop.exists() {
            return Ok(());
        }
        info!("load {} system.prop", module.display());

        // resetprop -n --file system.prop
        Command::new(assets::RESETPROP_PATH)
            .arg("-n")
            .arg("--file")
            .arg(&system_prop)
            .status()
            .with_context(|| format!("Failed to exec {}", system_prop.display()))?;

        Ok(())
    })?;

    Ok(())
}

pub fn prune_modules() -> Result<()> {
    foreach_module(false, |module| {
        fs::remove_file(module.join(defs::UPDATE_FILE_NAME)).ok();
        if !module.join(defs::REMOVE_FILE_NAME).exists() {
            return Ok(());
        }

        info!("remove module: {}", module.display());

        let uninstaller = module.join("uninstall.sh");
        if uninstaller.exists() {
            if let Err(e) = exec_script(uninstaller, true) {
                warn!("Failed to exec uninstaller: {}", e);
            }
        }

        if let Err(e) = fs::remove_dir_all(module) {
            warn!("Failed to remove {}: {}", module.display(), e);
        }
        let module_path = module.display().to_string();
        let updated_path = module_path.replace(defs::MODULE_DIR, defs::MODULE_UPDATE_TMP_DIR);

        if let Err(e) = fs::remove_dir_all(&updated_path) {
            warn!("Failed to remove {}: {}", updated_path, e);
        }
        Ok(())
    })?;

    Ok(())
}

fn _install_module(zip: &str) -> Result<()> {
    ensure_boot_completed()?;

    // print banner
    println!(include_str!("banner"));

    assets::ensure_binaries().with_context(|| "binary missing")?;

    // first check if workding dir is usable
    ensure_dir_exists(defs::WORKING_DIR).with_context(|| "Failed to create working dir")?;
    ensure_dir_exists(defs::BINARY_DIR).with_context(|| "Failed to create bin dir")?;

    // read the module_id from zip
    let mut buffer: Vec<u8> = Vec::new();
    let entry_path = PathBuf::from_str("module.prop")?;
    let zip_path = PathBuf::from_str(zip)?;
    let zip_path = zip_path.canonicalize()?;
    zip_extract_file_to_memory(&zip_path, &entry_path, &mut buffer)?;
    let mut module_prop = HashMap::new();
    PropertiesIter::new_with_encoding(Cursor::new(buffer), encoding_rs::UTF_8).read_into(
        |k, v| {
            module_prop.insert(k, v);
        },
    )?;
    info!("module prop: {:?}", module_prop);

    let Some(module_id) = module_prop.get("id") else {
        bail!("module id not found in module.prop!");
    };

    let modules_dir = Path::new(defs::MODULE_DIR);
    let modules_update_dir = Path::new(defs::MODULE_UPDATE_TMP_DIR);
    if !Path::new(modules_dir).exists() {
        fs::create_dir(modules_dir).expect("Failed to create modules folder");
        let permissions = fs::Permissions::from_mode(0o700);
        fs::set_permissions(modules_dir, permissions).expect("Failed to set permissions");
    }

    let module_dir = format!("{}{}", modules_dir.display(), module_id.clone());
    let _module_update_dir = format!("{}{}", modules_update_dir.display(), module_id.clone());
    info!("module dir: {}", module_dir);
    if !Path::new(&module_dir.clone()).exists() {
        fs::create_dir(&module_dir.clone()).expect("Failed to create module folder");
        let permissions = fs::Permissions::from_mode(0o700);
        fs::set_permissions(module_dir.clone(), permissions).expect("Failed to set permissions");
    }
    // unzip the image and move it to modules_update/<id> dir
    let file = fs::File::open(zip)?;
    let mut archive = zip::ZipArchive::new(file)?;
    archive.extract(&_module_update_dir)?;

    // set permission and selinux context for $MOD/system
    let module_system_dir = PathBuf::from(module_dir.clone()).join("system");
    if module_system_dir.exists() {
        #[cfg(unix)]
        fs::set_permissions(&module_system_dir, fs::Permissions::from_mode(0o755))?;
        restorecon::restore_syscon(&module_system_dir)?;
    }
    exec_install_script(zip)?;
    mark_update()?;
    Ok(())
}

pub fn install_module(zip: &str) -> Result<()> {
    let result = _install_module(zip);
    result
}

pub fn _uninstall_module(id: &str, update_dir: &str) -> Result<()> {
    let dir = Path::new(update_dir);
    ensure!(dir.exists(), "No module installed");

    // iterate the modules_update dir, find the module to be removed
    let dir = fs::read_dir(dir)?;
    for entry in dir.flatten() {
        let path = entry.path();
        let module_prop = path.join("module.prop");
        if !module_prop.exists() {
            continue;
        }
        let content = fs::read(module_prop)?;
        let mut module_id: String = String::new();
        PropertiesIter::new_with_encoding(Cursor::new(content), encoding_rs::UTF_8).read_into(
            |k, v| {
                if k.eq("id") {
                    module_id = v;
                }
            },
        )?;
        if module_id.eq(id) {
            let remove_file = path.join(defs::REMOVE_FILE_NAME);
            fs::File::create(remove_file).with_context(|| "Failed to create remove file.")?;
            break;
        }
    }

    // santity check
    let target_module_path = format!("{update_dir}/{id}");
    let target_module = Path::new(&target_module_path);
    if target_module.exists() {
        let remove_file = target_module.join(defs::REMOVE_FILE_NAME);
        if !remove_file.exists() {
            fs::File::create(remove_file).with_context(|| "Failed to create remove file.")?;
        }
    }

    let _ = mark_module_state(id, defs::REMOVE_FILE_NAME, true);
    Ok(())
}
pub fn uninstall_module(id: &str) -> Result<()> {
    _uninstall_module(id, defs::MODULE_DIR)?;
    Ok(())
}

pub fn run_action(id: &str) -> Result<()> {
    let action_script_path = format!("/data/adb/modules/{}/action.sh", id);
    let _ = exec_script(&action_script_path, true);
    Ok(())
}

fn _change_module_state(module_dir: &str, mid: &str, enable: bool) -> Result<()> {
    let src_module_path = format!("{module_dir}/{mid}");
    let src_module = Path::new(&src_module_path);
    ensure!(src_module.exists(), "module: {} not found!", mid);

    let disable_path = src_module.join(defs::DISABLE_FILE_NAME);
    if enable {
        if disable_path.exists() {
            fs::remove_file(&disable_path).with_context(|| {
                format!("Failed to remove disable file: {}", &disable_path.display())
            })?;
        }
    } else {
        ensure_file_exists(disable_path)?;
    }

    let _ = mark_module_state(mid, defs::DISABLE_FILE_NAME, !enable);

    Ok(())
}

pub fn _enable_module(id: &str, update_dir: &Path) -> Result<()> {
    if let Some(module_dir_str) = update_dir.to_str() {
        _change_module_state(module_dir_str, id, true)
    } else {
        info!("Enable module failed: Invalid path");
        Err(anyhow::anyhow!("Invalid module directory"))
    }
}

pub fn enable_module(id: &str) -> Result<()> {
    let update_dir = Path::new(defs::MODULE_DIR);
    _enable_module(id, update_dir)?;
    Ok(())
}

pub fn _disable_module(id: &str, update_dir: &Path) -> Result<()> {
    if let Some(module_dir_str) = update_dir.to_str() {
        _change_module_state(module_dir_str, id, false)
    } else {
        info!("Disable module failed: Invalid path");
        Err(anyhow::anyhow!("Invalid module directory"))
    }
}

pub fn disable_module(id: &str) -> Result<()> {
    let module_dir = Path::new(defs::MODULE_DIR);
    _disable_module(id, module_dir)?;

    Ok(())
}

pub fn _disable_all_modules(dir: &str) -> Result<()> {
    let dir = fs::read_dir(dir)?;
    for entry in dir.flatten() {
        let path = entry.path();
        let disable_flag = path.join(defs::DISABLE_FILE_NAME);
        if let Err(e) = ensure_file_exists(disable_flag) {
            warn!("Failed to disable module: {}: {}", path.display(), e);
        }
    }
    Ok(())
}

pub fn disable_all_modules() -> Result<()> {
    // Skip disabling modules since boot completed
    if getprop("sys.boot_completed").as_deref() == Some("1") {
        info!("System boot completed, no need to disable all modules");
        return Ok(());
    }
    mark_update()?;
    _disable_all_modules(defs::MODULE_DIR)?;
    Ok(())
}

fn _list_modules(path: &str) -> Vec<HashMap<String, String>> {
    // first check enabled modules
    let dir = fs::read_dir(path);
    let Ok(dir) = dir else {
        return Vec::new();
    };

    let mut modules: Vec<HashMap<String, String>> = Vec::new();

    for entry in dir.flatten() {
        let path = entry.path();
        info!("path: {}", path.display());
        let module_prop = path.join("module.prop");
        if !module_prop.exists() {
            continue;
        }
        let content = fs::read(&module_prop);
        let Ok(content) = content else {
            warn!("Failed to read file: {}", module_prop.display());
            continue;
        };
        let mut module_prop_map: HashMap<String, String> = HashMap::new();
        let encoding = encoding_rs::UTF_8;
        let result =
            PropertiesIter::new_with_encoding(Cursor::new(content), encoding).read_into(|k, v| {
                module_prop_map.insert(k, v);
            });

        if !module_prop_map.contains_key("id") || module_prop_map["id"].is_empty() {
            match entry.file_name().to_str() {
                Some(id) => {
                    info!("Use dir name as module id: {}", id);
                    module_prop_map.insert("id".to_owned(), id.to_owned());
                }
                _ => {
                    info!("Failed to get module id: {:?}", module_prop);
                    continue;
                }
            }
        }

        // Add enabled, update, remove flags
        let enabled = !path.join(defs::DISABLE_FILE_NAME).exists();
        let update = path.join(defs::UPDATE_FILE_NAME).exists();
        let remove = path.join(defs::REMOVE_FILE_NAME).exists();
        let web = path.join(defs::MODULE_WEB_DIR).exists();
        let action = path.join(defs::MODULE_ACTION_SH).exists();

        module_prop_map.insert("enabled".to_owned(), enabled.to_string());
        module_prop_map.insert("update".to_owned(), update.to_string());
        module_prop_map.insert("remove".to_owned(), remove.to_string());
        module_prop_map.insert("web".to_owned(), web.to_string());
        module_prop_map.insert("action".to_owned(), action.to_string());

        if result.is_err() {
            warn!("Failed to parse module.prop: {}", module_prop.display());
            continue;
        }
        modules.push(module_prop_map);
    }

    modules
}

pub fn list_modules() -> Result<()> {
    let modules = _list_modules(defs::MODULE_DIR);
    println!("{}", serde_json::to_string_pretty(&modules)?);
    Ok(())
}
