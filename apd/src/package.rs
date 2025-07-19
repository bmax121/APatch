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
    for i in 0..max_retry {
        let file = match File::open("/data/adb/ap/package_config") {
            Ok(file) => file,
            Err(e) => {
                warn!("Error opening file (attempt {}): {}", i + 1, e);
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
    return Vec::new();
}

fn write_ap_package_config(package_configs: &[PackageConfig]) {
    let max_retry = 5;

    for i in 0..max_retry {
        let file = match File::create("/data/adb/ap/package_config") {
            Ok(file) => file,
            Err(e) => {
                warn!("Error creating file (attempt {}): {}", i + 1, e);
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
        
        if success {
            return;
        }
        
        thread::sleep(Duration::from_secs(1));
    }
}

fn read_lines<P>(filename: P) -> io::Result<io::Lines<io::BufReader<File>>>
where
    P: AsRef<Path>,
{
    File::open(filename).map(|file| io::BufReader::new(file).lines())
}

pub fn synchronize_package_uid() {
    info!("[synchronize_package_uid] Start synchronizing root list with system packages...");

    if let Ok(lines) = read_lines("/data/system/packages.list") {
        let mut package_configs = read_ap_package_config();

        for line in lines.filter_map(|line| line.ok()) {
            let words: Vec<&str> = line.split_whitespace().collect();
            if words.len() >= 2 {
                if let Ok(uid) = words[1].parse::<i32>() {
                    if let Some(config) = package_configs
                        .iter_mut()
                        .find(|config| config.pkg == words[0])
                    {
                        config.uid = uid;
                    }
                } else {
                    warn!("Error parsing uid: {}", words[1]);
                }
            }
        }

        write_ap_package_config(&package_configs);
    }
}
