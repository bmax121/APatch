use log::{info, warn};
use serde::{Deserialize, Serialize};
use std::fs::File;
use std::io::{self, BufRead};
use std::path::Path;
use std::thread;
use std::time::Duration;

#[derive(Deserialize, Serialize)]
pub struct PackageConfig {
    pub pkg: String,
    pub exclude: i32,
    pub allow: i32,
    pub uid: i32,
    pub to_uid: i32,
    pub sctx: String,
}

pub fn read_ap_package_config() -> Vec<PackageConfig> {
    let max_retry = 5;
    for _ in 0..max_retry {
        let file = match File::open("/data/adb/ap/package_config") {
            Ok(file) => file,
            Err(e) => {
                warn!("Error opening file: {}", e);
                thread::sleep(Duration::from_secs(1));
                continue;
            }
        };

        let mut reader = csv::Reader::from_reader(file);
        let mut package_configs = Vec::new();
        let mut success = true;

        for record in reader.deserialize() {
            match record {
                Ok(config) => package_configs.push(config),
                Err(e) => {
                    warn!("Error deserializing record: {}", e);
                    success = false;
                    break;
                }
            }
        }

        if success {
            return package_configs;
        }
        thread::sleep(Duration::from_secs(1));
    }
    Vec::new()
}

pub fn write_ap_package_config(package_configs: &[PackageConfig]) -> io::Result<()> {
    let max_retry = 5;
    for _ in 0..max_retry {
        let temp_path = "/data/adb/ap/package_config.tmp";
        let file = match File::create(temp_path) {
            Ok(file) => file,
            Err(e) => {
                warn!("Error creating temp file: {}", e);
                thread::sleep(Duration::from_secs(1));
                continue;
            }
        };

        let mut writer = csv::Writer::from_writer(file);
        let mut success = true;

        for config in package_configs {
            if let Err(e) = writer.serialize(config) {
                warn!("Error serializing record: {}", e);
                success = false;
                break;
            }
        }

        if !success {
            thread::sleep(Duration::from_secs(1));
            continue;
        }

        if let Err(e) = writer.flush() {
            warn!("Error flushing writer: {}", e);
            thread::sleep(Duration::from_secs(1));
            continue;
        }

        if let Err(e) = std::fs::rename(temp_path, "/data/adb/ap/package_config") {
            warn!("Error renaming temp file: {}", e);
            thread::sleep(Duration::from_secs(1));
            continue;
        }
        return Ok(());
    }
    Err(io::Error::new(
        io::ErrorKind::Other,
        "Failed after max retries",
    ))
}

fn read_lines<P>(filename: P) -> io::Result<io::Lines<io::BufReader<File>>>
where
    P: AsRef<Path>,
{
    File::open(filename).map(|file| io::BufReader::new(file).lines())
}

pub fn synchronize_package_uid() -> io::Result<()> {
    info!("[synchronize_package_uid] Start synchronizing root list with system packages...");

    let max_retry = 5;
    for _ in 0..max_retry {
        match read_lines("/data/system/packages.list") {
            Ok(lines) => {
                let lines: Vec<_> = lines.filter_map(|line| line.ok()).collect();
                
                let mut package_configs = read_ap_package_config();
                
                // 获取当前系统中的所有包名
                let system_packages: Vec<String> = lines
                    .iter()
                    .filter_map(|line| line.split_whitespace().next())
                    .map(|pkg| pkg.to_string())
                    .collect();

                // 清理已卸载的软件配置（防止残留）
                let original_len = package_configs.len();
                package_configs.retain(|config| system_packages.contains(&config.pkg));
                let removed_count = original_len - package_configs.len();
                
                if removed_count > 0 {
                    info!("Removed {} uninstalled package configurations", removed_count);
                }

                // 只更新已存在的配置，不添加新配置（防止未授权权限获取）
                let mut updated = false;

                for line in &lines {
                    let words: Vec<&str> = line.split_whitespace().collect();
                    if words.len() >= 2 {
                        let pkg_name = words[0];
                        if let Ok(uid) = words[1].parse::<i32>() {
                            if let Some(config) = package_configs
                                .iter_mut()
                                .find(|config| config.pkg == pkg_name)
                            {
                                // 只更新已授权配置的uid，不添加新配置
                                if config.uid != uid {
                                    info!("Updating uid for package {}: {} -> {}", pkg_name, config.uid, uid);
                                    config.uid = uid;
                                    updated = true;
                                }
                            }
                        } else {
                            warn!("Error parsing uid: {}", words[1]);
                        }
                    }
                }

                if updated || removed_count > 0 {
                    write_ap_package_config(&package_configs)?;
                }
                return Ok(());
            }
            Err(e) => {
                warn!("Error reading packages.list: {}", e);
                thread::sleep(Duration::from_secs(1));
            }
        }
    }
    Err(io::Error::new(
        io::ErrorKind::Other,
        "Failed after max retries",
    ))
}

/// 添加新包配置时进行权限验证
pub fn add_package_config(new_config: PackageConfig) -> io::Result<bool> {
    let mut package_configs = read_ap_package_config();
    
    // 检查是否已存在
    if package_configs.iter().any(|c| c.pkg == new_config.pkg) {
        warn!("Package {} already exists in configuration", new_config.pkg);
        return Ok(false);
    }
    
    // 验证包是否存在于系统中
    match read_lines("/data/system/packages.list") {
        Ok(lines) => {
            let exists = lines
                .filter_map(|line| line.ok())
                .any(|line| line.starts_with(&format!("{} ", new_config.pkg)));
            
            if !exists {
                warn!("Package {} not found in system", new_config.pkg);
                return Ok(false);
            }
        }
        Err(e) => {
            warn!("Error checking package existence: {}", e);
            return Err(e);
        }
    }
    
    // 添加到配置
    package_configs.push(new_config);
    write_ap_package_config(&package_configs)?;
    info!("Added new package configuration for {}", new_config.pkg);
    Ok(true)
}

/// 删除指定包的配置
pub fn remove_package_config(pkg_name: &str) -> io::Result<bool> {
    let mut package_configs = read_ap_package_config();
    
    let original_len = package_configs.len();
    package_configs.retain(|config| config.pkg != pkg_name);
    
    if package_configs.len() < original_len {
        write_ap_package_config(&package_configs)?;
        info!("Removed package configuration for {}", pkg_name);
        Ok(true)
    } else {
        warn!("Package {} not found in configuration", pkg_name);
        Ok(false)
    }
}
