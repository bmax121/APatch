use serde::{Deserialize, Serialize};
use std::fs::File;
use std::io::{self, BufRead};
use std::path::Path;

#[derive(Deserialize, Serialize)] // Add Serialize trait
pub struct PackageConfig {
    pub pkg: String,
    pub exclude: i32,
    pub allow: i32,
    pub uid: i32,
    pub to_uid: i32,
    pub sctx: String,
}

pub fn read_ap_package_config() -> Vec<PackageConfig> {
    let file = match File::open("/data/adb/ap/package_config") {
        Ok(file) => file,
        Err(e) => {
            warn!("Error opening file: {}", e);
            return Vec::new();
        }
    };
    let mut reader = csv::Reader::from_reader(file);

    let mut package_configs = Vec::new();
    for record in reader.deserialize() {
        match record {
            Ok(config) => package_configs.push(config),
            Err(e) => {
                warn!("Error deserializing record: {}", e);
            }
        }
    }

    package_configs
}

fn write_ap_package_config(package_configs: &Vec<PackageConfig>) {
    let file = match File::create("/data/adb/ap/package_config") {
        Ok(file) => file,
        Err(e) => {
            warn!("Error creating file: {}", e);
            return;
        }
    };
    let mut writer = csv::Writer::from_writer(file);

    for config in package_configs {
        if let Err(e) = writer.serialize(config) {
            warn!("Error serializing record: {}", e);
        }
    }
}

fn read_lines<P>(filename: P) -> io::Result<io::Lines<io::BufReader<File>>>
where
    P: AsRef<Path>,
{
    let file = File::open(filename)?;
    Ok(io::BufReader::new(file).lines())
}

pub fn synchronize_package_uid() {
    if let Ok(lines) = read_lines("/data/system/packages.list") {
        let mut package_configs = read_ap_package_config();
        for line in lines {
            if let Ok(line) = line {
                let words: Vec<&str> = line.split_whitespace().collect();
                if words.len() >= 2 {
                    if let Ok(uid) = words[1].parse::<i32>() {
                        package_configs
                            .iter_mut() // Use iter_mut() to get mutable references
                            .find(|config| config.pkg == words[0])
                            .map(|config| {
                                config.uid = uid;
                            });
                    } else {
                        warn!("Error parsing uid: {}", words[1]);
                    }
                }
            }
        }
        write_ap_package_config(&package_configs);
    }
}
