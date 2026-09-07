use std::{
    fs::File,
    io::{self, BufRead},
    path::Path,
    thread,
    time::Duration,
};

use log::{info, warn};
use serde::{Deserialize, Serialize};

#[derive(Deserialize, Serialize, Clone)]
pub struct PackageConfig {
    pub pkg: String,
    pub exclude: i32,
    pub allow: i32,
    pub uid: i32,
    pub to_uid: i32,
    pub sctx: String,
}

/// Outcome of reading package_config, distinguishing a legitimately empty
/// config (header-only, i.e. user revoked everything) from a torn/unreadable
/// one (missing, 0-byte, truncated) that must NOT trigger revokes.
pub struct PackageConfigRead {
    pub configs: Vec<PackageConfig>,
    /// true if the file was opened and every row parsed cleanly.
    /// Header-only parses as valid-but-empty. 0-byte / missing / dirty
    /// files are invalid after retries.
    pub valid: bool,
}

fn strip_bom(s: &str) -> &str {
    s.trim_start_matches('\u{FEFF}')
}

fn is_header_record(record: &csv::StringRecord) -> bool {
    record
        .get(0)
        .map(|f| strip_bom(f.trim()) == "pkg")
        .unwrap_or(false)
}

fn is_blank_record(record: &csv::StringRecord) -> bool {
    record.iter().all(|f| f.trim().is_empty())
}

pub fn read_ap_package_config_validated() -> PackageConfigRead {
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

        // has_headers(false): the app and apd both emit a header line, but
        // older tools / manual edits may leave the file headerless. With the
        // default has_headers(true) a headerless file would silently swallow
        // its first grant row, so parse every row and skip the literal header
        // by content instead. The header must be detected on the raw
        // StringRecord: deserializing it into PackageConfig (i32 fields)
        // would always fail with `invalid digit` before any content check.
        let mut reader = csv::ReaderBuilder::new()
            .has_headers(false)
            .trim(csv::Trim::All)
            .flexible(true)
            .from_reader(file);
        let mut package_configs = Vec::new();
        let mut success = true;
        let mut saw_any_row = false;

        for record in reader.records() {
            let record = match record {
                Ok(record) => record,
                Err(e) => {
                    warn!("Error reading CSV record: {}", e);
                    success = false;
                    break;
                }
            };
            if is_blank_record(&record) {
                continue;
            }
            saw_any_row = true;
            if is_header_record(&record) {
                continue;
            }
            match record.deserialize::<PackageConfig>(None) {
                Ok(mut config) => {
                    // A hand-edited file may carry a UTF-8 BOM on the first
                    // data row; strip it so pkg matching / retain() works.
                    if config.pkg.starts_with('\u{FEFF}') {
                        config.pkg = strip_bom(&config.pkg).to_owned();
                    }
                    package_configs.push(config)
                }
                Err(e) => {
                    warn!("Error deserializing record: {}", e);
                    success = false;
                    break;
                }
            }
        }

        // A 0-byte / blank-only file has no header and no rows: almost
        // certainly a torn read, not a legit "revoke everything".
        if success && !saw_any_row {
            warn!("package_config has no parsable rows (empty?), treating as torn read");
            success = false;
        }

        if success {
            return PackageConfigRead {
                configs: package_configs,
                valid: true,
            };
        }
        thread::sleep(Duration::from_secs(1));
    }
    PackageConfigRead {
        configs: Vec::new(),
        valid: false,
    }
}

#[allow(dead_code)]
pub fn read_ap_package_config() -> Vec<PackageConfig> {
    read_ap_package_config_validated().configs
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
    Err(io::Error::other("Failed after max retries"))
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
                // Skip bad lines instead of `map_while(Result::ok)`: truncating
                // at the first error would drop the tail of the list, and the
                // retain() below would then revoke grants for those packages.
                #[allow(clippy::lines_filter_map_ok)]
                let lines: Vec<_> = lines.filter_map(|line| line.ok()).collect();

                // A torn package_config read must fail-open: syncing (and
                // persisting) against an empty snapshot would drop grants.
                let read = read_ap_package_config_validated();
                if !read.valid {
                    warn!("[synchronize_package_uid] package_config unreadable, skipping sync");
                    return Err(io::Error::other("package_config is unreadable"));
                }
                let mut package_configs = read.configs;

                let system_packages: Vec<String> = lines
                    .iter()
                    .filter_map(|line| line.split_whitespace().next())
                    .map(|pkg| pkg.to_string())
                    .collect();

                // An empty/blank packages.list is almost certainly a torn read
                // (PackageManager rewrites it via .tmp + rename) rather than a
                // device with no packages. Retaining against it would wipe every
                // persisted config below, so refuse to sync in that case.
                if system_packages.is_empty() {
                    warn!("[synchronize_package_uid] packages.list has no parsable entries, aborting sync");
                    return Err(io::Error::other("packages.list is empty"));
                }

                let original_len = package_configs.len();
                package_configs.retain(|config| system_packages.contains(&config.pkg));
                let removed_count = original_len - package_configs.len();

                if removed_count > 0 {
                    info!(
                        "Removed {} uninstalled package configurations",
                        removed_count
                    );
                }

                let mut updated = false;

                for line in &lines {
                    let words: Vec<&str> = line.split_whitespace().collect();
                    if words.len() >= 2 {
                        let pkg_name = words[0];
                        if let Ok(uid) = words[1].parse::<i32>() {
                            for config in package_configs
                                .iter_mut()
                                .filter(|config| config.pkg == pkg_name)
                            {
                                if config.uid % 100000 != uid % 100000 {
                                    let new_uid = config.uid / 100000 * 100000 + uid % 100000;
                                    info!(
                                        "Updating uid for package {}: {} -> {}",
                                        pkg_name, config.uid, new_uid
                                    );
                                    config.uid = new_uid;
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
    Err(io::Error::other("Failed after max retries"))
}
