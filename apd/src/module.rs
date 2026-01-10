#[cfg(unix)]
use std::os::unix::{prelude::PermissionsExt, process::CommandExt};
use std::{
    collections::HashMap,
    env::var as env_var,
    fs::{self, remove_dir_all},
    io::Cursor,
    path::{Path, PathBuf},
    process::Command,
    str::FromStr,
};

use anyhow::{Context, Result, anyhow, bail, ensure};
use const_format::concatcp;
use is_executable::is_executable;
use java_properties::PropertiesIter;
use log::{info, warn};
use mlua::{Function, Lua, Result as LuaResult, Table};
use zip_extensions::zip_extract_file_to_memory;

#[allow(clippy::wildcard_imports)]
use crate::utils::*;
use crate::{
    assets,
    defs::{self, MODULE_DIR, MODULE_UPDATE_DIR},
    metamodule, restorecon,
};

const INSTALLER_CONTENT: &str = include_str!("./installer.sh");
const INSTALL_MODULE_SCRIPT: &str = concatcp!(
    INSTALLER_CONTENT,
    "\n",
    "install_module",
    "\n",
    "exit 0",
    "\n"
);

#[derive(PartialEq, Eq)]
pub enum ModuleType {
    All,
    Active,
    Updated,
}

fn exec_install_script(module_file: &str, is_metamodule: bool) -> Result<()> {
    let realpath = std::fs::canonicalize(module_file)
        .with_context(|| format!("realpath: {module_file} failed"))?;

    // Get install script from metamodule module
    let install_script =
        metamodule::get_install_script(is_metamodule, INSTALLER_CONTENT, INSTALL_MODULE_SCRIPT)?;

    let result = Command::new(assets::BUSYBOX_PATH)
        .args(["sh", "-c", &install_script])
        .envs(get_common_script_envs())
        .env("OUTFD", "1")
        .env("ZIPFILE", realpath)
        .status()?;
    ensure!(result.success(), "Failed to install module script");
    Ok(())
}

pub fn handle_updated_modules() -> Result<()> {
    let modules_root = Path::new(MODULE_DIR);
    foreach_module(ModuleType::Updated, |updated_module| {
        if !updated_module.is_dir() {
            return Ok(());
        }

        if let Some(name) = updated_module.file_name() {
            let module_dir = modules_root.join(name);
            let mut disabled = false;
            let mut removed = false;
            if module_dir.exists() {
                // If the old module is disabled, we need to also disable the new one
                disabled = module_dir.join(defs::DISABLE_FILE_NAME).exists();
                removed = module_dir.join(defs::REMOVE_FILE_NAME).exists();
                remove_dir_all(&module_dir)?;
            }
            std::fs::rename(updated_module, &module_dir)?;
            if removed {
                let path = module_dir.join(defs::REMOVE_FILE_NAME);
                if let Err(e) = ensure_file_exists(&path) {
                    warn!("Failed to create {}: {e}", path.display());
                }
            } else if disabled {
                let path = module_dir.join(defs::DISABLE_FILE_NAME);
                if let Err(e) = ensure_file_exists(&path) {
                    warn!("Failed to create {}: {e}", path.display());
                }
            }
        }
        Ok(())
    })?;
    Ok(())
}

/// Get common environment variables for script execution
pub fn get_common_script_envs() -> Vec<(&'static str, String)> {
    vec![
        ("ASH_STANDALONE", "1".to_string()),
        ("APATCH", "true".to_string()),
        ("APATCH_VER", defs::VERSION_NAME.to_string()),
        ("APATCH_VER_CODE", defs::VERSION_CODE.to_string()),
        (
            "PATH",
            format!(
                "{}:{}",
                env_var("PATH").unwrap_or_default(),
                defs::BINARY_DIR.trim_end_matches('/')
            ),
        ),
    ]
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
pub fn foreach_module(
    module_type: ModuleType,
    mut f: impl FnMut(&Path) -> Result<()>,
) -> Result<()> {
    let modules_dir = Path::new(match module_type {
        ModuleType::Updated => MODULE_UPDATE_DIR,
        _ => defs::MODULE_DIR,
    });
    let dir = std::fs::read_dir(modules_dir)?;
    for entry in dir.flatten() {
        let path = entry.path();
        if !path.is_dir() {
            warn!("{} is not a directory, skip", path.display());
            continue;
        }

        if module_type == ModuleType::Active && path.join(defs::DISABLE_FILE_NAME).exists() {
            info!("{} is disabled, skip", path.display());
            continue;
        }
        if module_type == ModuleType::Active && path.join(defs::REMOVE_FILE_NAME).exists() {
            warn!("{} is removed, skip", path.display());
            continue;
        }

        f(&path)?;
    }

    Ok(())
}

fn foreach_active_module(f: impl FnMut(&Path) -> Result<()>) -> Result<()> {
    foreach_module(ModuleType::Active, f)
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

pub fn exec_script<T: AsRef<Path>>(path: T, wait: bool) -> Result<()> {
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

pub fn exec_stage_lua(stage: &str, wait: bool, superkey: &str) -> Result<()> {
    let stage_safe = stage.replace('-', "_");
    run_lua(&superkey, &stage_safe, true, wait).map_err(|e| anyhow::anyhow!("{}", e))?;
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
    foreach_module(ModuleType::All, |module| {
        fs::remove_file(module.join(defs::UPDATE_FILE_NAME)).ok();
        if !module.join(defs::REMOVE_FILE_NAME).exists() {
            return Ok(());
        }

        info!("remove module: {}", module.display());

        // Execute metamodule's metauninstall.sh first
        let module_id = module.file_name().and_then(|n| n.to_str()).unwrap_or("");

        // Check if this is a metamodule
        let is_metamodule = read_module_prop(module)
            .map(|props| metamodule::is_metamodule(&props))
            .unwrap_or(false);

        if is_metamodule {
            info!("Removing metamodule symlink");
            if let Err(e) = metamodule::remove_symlink() {
                warn!("Failed to remove metamodule symlink: {e}");
            }
        } else if let Err(e) = metamodule::exec_metauninstall_script(module_id) {
            warn!("Failed to exec metamodule uninstall for {module_id}: {e}",);
        }

        // Then execute module's own uninstall.sh
        let uninstaller = module.join("uninstall.sh");
        if uninstaller.exists()
            && let Err(e) = exec_script(uninstaller, true)
        {
            warn!("Failed to exec uninstaller: {e}");
        }

        // Finally remove the module directory
        if let Err(e) = remove_dir_all(module) {
            warn!("Failed to remove {}: {e}", module.display());
        }

        Ok(())
    })?;

    // collect remaining modules, if none, clean up metamodule record
    let remaining_modules: Vec<_> = std::fs::read_dir(defs::MODULE_DIR)?
        .filter_map(std::result::Result::ok)
        .filter(|entry| entry.path().join("module.prop").exists())
        .collect();

    if remaining_modules.is_empty() {
        info!("no remaining modules.");
    }

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
    let module_id = module_id.trim();

    // Check if this module is a metamodule
    let is_metamodule = metamodule::is_metamodule(&module_prop);

    // Check if it's safe to install regular module
    if !is_metamodule && let Err(is_disabled) = metamodule::check_install_safety() {
        println!("\n❌ Installation Blocked");
        println!("┌────────────────────────────────");
        println!("│ A metamodule with custom installer is active");
        println!("│");
        if is_disabled {
            println!("│ Current state: Disabled");
            println!("│ Action required: Re-enable or uninstall it, then reboot");
        } else {
            println!("│ Current state: Pending changes");
            println!("│ Action required: Reboot to apply changes first");
        }
        println!("└─────────────────────────────────\n");
        bail!("Metamodule installation blocked");
    }

    let modules_dir = Path::new(defs::MODULE_DIR);
    let modules_update_dir = Path::new(defs::MODULE_UPDATE_DIR);
    if !Path::new(modules_dir).exists() {
        fs::create_dir(modules_dir).expect("Failed to create modules folder");
        let permissions = fs::Permissions::from_mode(0o700);
        fs::set_permissions(modules_dir, permissions).expect("Failed to set permissions");
    }

    if is_metamodule {
        info!("Installing metamodule: {module_id}");

        // Check if there's already a metamodule installed
        if metamodule::has_metamodule()
            && let Some(existing_path) = metamodule::get_metamodule_path()
        {
            let existing_id = read_module_prop(&existing_path)
                .ok()
                .and_then(|m| m.get("id").cloned())
                .unwrap_or_else(|| "unknown".to_string());

            if existing_id != module_id {
                println!("\n❌ Installation Failed");
                println!("┌────────────────────────────────");
                println!("│ A metamodule is already installed");
                println!("│   Current metamodule: {existing_id}");
                println!("│");
                println!("│ Only one metamodule can be active at a time.");
                println!("│");
                println!("│ To install this metamodule:");
                println!("│   1. Uninstall the current metamodule");
                println!("│   2. Reboot your device");
                println!("│   3. Install the new metamodule");
                println!("└─────────────────────────────────\n");
                bail!("Cannot install multiple metamodules");
            }
        }
    }

    let module_dir = format!("{}{}", modules_dir.display(), module_id);
    let _module_update_dir = format!("{}{}", modules_update_dir.display(), module_id);
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

    println!("- Running module installer");
    exec_install_script(zip, is_metamodule)?;

    // set permission and selinux context for $MOD/system
    let module_system_dir = PathBuf::from(module_dir.clone()).join("system");
    if module_system_dir.exists() {
        #[cfg(unix)]
        fs::set_permissions(&module_system_dir, fs::Permissions::from_mode(0o755))?;
        restorecon::restore_syscon(&module_system_dir)?;
    }

    // Create symlink for metamodule
    if is_metamodule {
        println!("- Creating metamodule symlink");
        metamodule::ensure_symlink(&module_dir)?;
    }

    exec_install_script(zip, is_metamodule)?;
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
    mark_update()?;
    Ok(())
}

/// Read module.prop from the given module path and return as a HashMap
pub fn read_module_prop(module_path: &Path) -> Result<HashMap<String, String>> {
    let module_prop = module_path.join("module.prop");
    ensure!(
        module_prop.exists(),
        "module.prop not found in {}",
        module_path.display()
    );

    let content = std::fs::read(&module_prop)
        .with_context(|| format!("Failed to read module.prop: {}", module_prop.display()))?;

    let mut prop_map: HashMap<String, String> = HashMap::new();
    PropertiesIter::new_with_encoding(Cursor::new(content), encoding_rs::UTF_8)
        .read_into(|k, v| {
            prop_map.insert(k, v);
        })
        .with_context(|| format!("Failed to parse module.prop: {}", module_prop.display()))?;

    Ok(prop_map)
}

pub fn save_text<P: AsRef<Path>>(filename: P, content: &str) -> std::io::Result<()> {
    let _ = ensure_dir_exists("/data/adb/config");
    let path = Path::new("/data/adb/config").join(filename);
    fs::write(path, content)?;
    Ok(())
}

pub fn load_text<P: AsRef<Path>>(filename: P) -> std::io::Result<String> {
    let _ = ensure_dir_exists("/data/adb/config");
    let path = Path::new("/data/adb/config").join(filename);
    fs::read_to_string(path)
}

pub fn load_all_lua_modules(lua: &Lua) -> LuaResult<()> {
    let modules_dir = Path::new("/data/adb/modules");

    let modules: Table = match lua.globals().get("modules") {
        Ok(t) => t,
        Err(_) => {
            let t = lua.create_table()?;
            lua.globals().set("modules", t.clone())?;
            t
        }
    };

    if modules_dir.exists() {
        for entry in
            fs::read_dir(modules_dir).unwrap_or_else(|_| fs::read_dir("/dev/null").unwrap())
        {
            if let Ok(entry) = entry {
                let path = entry.path();
                if path.is_dir() {
                    let id = path.file_name().unwrap().to_string_lossy().to_string();
                    let package: Table = lua.globals().get("package")?;
                    let old_cpath: String = package.get("cpath")?;
                    let new_cpath = format!("{}/?.so;{}", path.to_string_lossy(), old_cpath);
                    package.set("cpath", new_cpath)?;

                    let lua_file = path.join(format!("{}.lua", id));

                    if lua_file.exists() {
                        match fs::read_to_string(&lua_file) {
                            Ok(code) => {
                                match lua
                                    .load(&code)
                                    .set_name(&*lua_file.to_string_lossy())
                                    .eval::<Table>()
                                {
                                    Ok(module) => {
                                        modules.set(id.clone(), module.clone())?;
                                    }
                                    Err(e) => {
                                        eprintln!(
                                            "Failed to eval Lua {}: {}",
                                            lua_file.display(),
                                            e
                                        );
                                    }
                                }
                            }
                            Err(e) => {
                                eprintln!("Failed to read Lua {}: {}", lua_file.display(), e);
                            }
                        }
                    }
                }
            }
        }
    }

    Ok(())
}

pub fn info_lua(lua: &Lua) -> LuaResult<Function> {
    lua.create_function(|_, msg: String| {
        info!("[Lua] {}", msg);
        Ok(())
    })
}

pub fn warn_lua(lua: &Lua) -> LuaResult<Function> {
    lua.create_function(|_, msg: String| {
        warn!("[Lua] {}", msg);
        Ok(())
    })
}

pub fn install_module_lua(lua: &Lua) -> LuaResult<Function> {
    lua.create_function(|_, zip: String| {
        install_module(&zip)
            .map_err(|e| mlua::Error::external(format!("install_module failed: {}", e)))
    })
}
pub fn save_text_lua(lua: &Lua) -> LuaResult<Function> {
    lua.create_function(|_, (filename, content): (String, String)| {
        save_text(&filename, &content)
            .map_err(|e| mlua::Error::external(format!("save filed: {}", e)))?;
        Ok(())
    })
}
pub fn read_text_lua(lua: &Lua) -> LuaResult<Function> {
    lua.create_function(|_, filename: String| {
        let content = match load_text(&filename) {
            Ok(s) => s,
            Err(ref e) if e.kind() == std::io::ErrorKind::NotFound => String::new(),
            Err(e) => return Err(mlua::Error::external(format!("read failed: {}", e))),
        };
        Ok(content)
    })
}

pub fn run_lua(id: &str, function: &str, on_each_module: bool, _wait: bool) -> mlua::Result<()> {
    let lua = unsafe { Lua::unsafe_new() };

    let func = install_module_lua(&lua)?;
    lua.globals().set("install_module", func)?;
    lua.globals().set("info", info_lua(&lua)?)?;
    lua.globals().set("warn", warn_lua(&lua)?)?;
    lua.globals().set("setConfig", save_text_lua(&lua)?)?;
    lua.globals().set("getConfig", read_text_lua(&lua)?)?;

    load_all_lua_modules(&lua)?;

    let modules: mlua::Table = lua.globals().get("modules")?;
    if on_each_module {
        for pair in modules.pairs::<String, mlua::Table>() {
            let (_, module_table) = pair?;
            if let Ok(func_obj) = module_table.get::<mlua::Function>(function) {
                func_obj.call::<()>(id)?;
            }
        }
    } else {
        let module_table: mlua::Table = modules.get(id)?;
        let func_obj: mlua::Function = module_table.get(function)?;
        func_obj.call::<()>(())?;
    }

    Ok(())
}
pub fn run_action(id: &str) -> Result<()> {
    let action_script_path = format!("/data/adb/modules/{}/action.sh", id);
    if Path::new(&action_script_path).exists() {
        let _ = exec_script(&action_script_path, true);
    } else {
        //if no action.sh, try to run lua action
        run_lua(&id, "action", false, true).map_err(|e| anyhow::anyhow!("{}", e))?;
    }
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
        let id = module_prop_map.get("id").map(|s| s.as_str()).unwrap_or("");
        let id_lua_file = format!("{}.lua", id);
        let action = path.join(defs::MODULE_ACTION_SH).exists() || path.join(&id_lua_file).exists();

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
