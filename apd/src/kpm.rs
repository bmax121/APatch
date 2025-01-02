use std::ffi::{CStr, CString};
use serde::{Deserialize, Serialize};

use crate::supercall;

#[derive(Serialize, Deserialize)]
struct ModuleInfo {
    name: String,
    version: String,
    author: String,
    description: String,
    args: String,
    license: String,
}


fn module_info(key_cstr: CString, name: String) -> anyhow::Result<ModuleInfo> {
    let name_cstr = CString::new(name).map_err(|_| anyhow::anyhow!("Invalid name string"))?;
    let mut info_buf = vec![0; 4096];
    let ret = supercall::sc_kpm_info(key_cstr.as_c_str(), name_cstr.as_c_str(), &mut info_buf);
    if ret < 0 {
        return Err(anyhow::anyhow!("System call failed with error code {}", ret))
    }

    let info = CStr::from_bytes_until_nul(&info_buf)
        .map_err(|_| anyhow::anyhow!("Invalid info buffer"))?
        .to_str()
        .map_err(|_| anyhow::anyhow!("Invalid UTF-8 in info buffer"))?;

    let info: ModuleInfo = info
        .lines()
        .filter_map(|line| line.split_once('='))
        .fold(ModuleInfo {
            name: String::new(),
            version: String::new(), 
            author: String::new(),
            description: String::new(),
            args: String::new(),
            license: String::new(),
        }, |mut info, (key, value)| {
            match key {
                "name" => info.name = value.to_owned(),
                "version" => info.version = value.to_owned(),
                "author" => info.author = value.to_owned(),
                "description" => info.description = value.to_owned(),
                "args" => info.args = value.to_owned(),
                "license" => info.license = value.to_owned(),
                _ => (),
            }
            info
        });

    return Ok(info)
}

pub fn load_module(superkey: Option<String>, path: String) -> anyhow::Result<()> {
    let key_cstr = match superkey {
        Some(x) => CString::new(x).map_err(|_| anyhow::anyhow!("Invalid key string"))?,
        None => return Err(anyhow::anyhow!("No superkey provided")),
    };
    let path_cstr = CString::new(path).map_err(|_| anyhow::anyhow!("Invalid path string"))?;
    let ret = supercall::sc_kpm_load(key_cstr.as_c_str(),path_cstr.as_c_str(),None,std::ptr::null_mut());
    if ret < 0 {
        return Err(anyhow::anyhow!("System call failed with error code {}", ret))
    } else {
        return Ok(())
    }
}

pub fn list_modules(superkey: Option<String>) -> anyhow::Result<()> {
    let key_cstr: CString = match superkey {
        Some(x) => CString::new(x).map_err(|_| anyhow::anyhow!("Invalid key string"))?,
        None => return Err(anyhow::anyhow!("No superkey provided")),
    };
    let mut names_buf = vec![0; 4096];
    let ret = supercall::sc_kpm_list(key_cstr.as_c_str(), &mut names_buf);
    if ret < 0 {
        return Err(anyhow::anyhow!("System call failed with error code {}", ret))
    }

    let names = CStr::from_bytes_until_nul(&names_buf)
        .map_err(|_| anyhow::anyhow!("Invalid module names buffer"))?
        .to_str()
        .map_err(|_| anyhow::anyhow!("Invalid UTF-8 in module names"))?;

    let infos: Vec<_> = names
        .split('\n')
        .filter(|name| !name.is_empty())
        .map(|name| module_info(key_cstr.clone(), name.to_string()))
        .collect::<Result<_, _>>()?;

    println!("{}", serde_json::to_string_pretty(&infos)?);

    return Ok(())
}