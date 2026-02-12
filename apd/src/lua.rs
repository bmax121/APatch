use crate::module::*;
use crate::utils::*;
use anyhow::Result;
use log::{info, warn};
use mlua::{Function, Lua, Result as LuaResult, Table};
use std::{fs, path::Path};

pub fn save_text<P: AsRef<Path>>(filename: P, content: &str) -> std::io::Result<()> {
    let _ = ensure_dir_exists("/data/adb/config");
    let path = format!("/data/adb/config/{}", filename.as_ref().display());
    fs::write(&path, content)?;
    Ok(())
}

pub fn load_text<P: AsRef<Path>>(filename: P) -> std::io::Result<String> {
    let _ = ensure_dir_exists("/data/adb/config");
    let path = format!("/data/adb/config/{}", filename.as_ref().display());
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

pub fn exec_stage_lua(stage: &str, wait: bool, superkey: &str) -> Result<()> {
    let stage_safe = stage.replace('-', "_");
    run_lua(&superkey, &stage_safe, true, wait).map_err(|e| anyhow::anyhow!("{}", e))?;
    Ok(())
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
