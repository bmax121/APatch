use std::env;
use std::fs::File;
use std::io::Write;
use std::path::Path;
use std::process::Command;

// app/src/main/cpp/version is the single source of the KernelPatch version;
// both this crate and the root build.gradle.kts derive their copy from it.
fn get_kp_version() -> (u32, u32, u32) {
    let header = std::fs::read_to_string("../app/src/main/cpp/version")
        .expect("Failed to read ../app/src/main/cpp/version");
    let parse = |name: &str| -> u32 {
        header
            .lines()
            .find_map(|line| {
                line.strip_prefix(format!("#define {name} ").as_str())
                    .and_then(|v| v.trim().parse().ok())
            })
            .unwrap_or_else(|| panic!("{name} not found in app/src/main/cpp/version"))
    };
    (parse("MAJOR"), parse("MINOR"), parse("PATCH"))
}

// version.properties at the repo root is the single source of the manager
// version code epoch; build.gradle.kts and CI derive theirs from it as well.
fn get_version_epoch() -> u32 {
    std::fs::read_to_string("../version.properties")
        .expect("Failed to read ../version.properties")
        .lines()
        .find_map(|line| {
            line.strip_prefix("managerVersionEpoch=")
                .and_then(|v| v.trim().parse().ok())
        })
        .expect("managerVersionEpoch not found in version.properties")
}

fn get_git_version() -> Result<(u32, String), std::io::Error> {
    let output = Command::new("git")
        .args(["rev-list", "--count", "HEAD"])
        .output()?;

    let output = output.stdout;
    let version_code = String::from_utf8(output).expect("Failed to read git count stdout");
    let version_code: u32 = version_code
        .trim()
        .parse()
        .map_err(|_| std::io::Error::new(std::io::ErrorKind::Other, "Failed to parse git count"))?;
    let version_code = get_version_epoch() + version_code;

    let version_name = String::from_utf8(
        Command::new("git")
            .args(["describe", "--tags", "--always"])
            .output()?
            .stdout,
    )
    .map_err(|_| {
        std::io::Error::new(
            std::io::ErrorKind::Other,
            "Failed to read git describe stdout",
        )
    })?;
    let version_name = version_name.trim_start_matches('v').to_string();
    Ok((version_code, version_name))
}

fn main() {
    // update VersionCode when git repository change
    println!("cargo:rerun-if-changed=../.git/HEAD");
    println!("cargo:rerun-if-changed=../.git/refs/");
    println!("cargo:rerun-if-changed=../app/src/main/cpp/version");
    println!("cargo:rerun-if-changed=../version.properties");

    let (code, name) = match get_git_version() {
        Ok((code, name)) => (code, name),
        Err(_) => {
            // show warning if git is not installed
            println!("cargo:warning=Failed to get git version, using 0.0.0");
            (0, "0.0.0".to_string())
        }
    };
    let out_dir = env::var("OUT_DIR").expect("Failed to get $OUT_DIR");
    println!("out_dir: ${out_dir}");
    println!("code: ${code}");
    let out_dir = Path::new(&out_dir);
    File::create(Path::new(out_dir).join("VERSION_CODE"))
        .expect("Failed to create VERSION_CODE")
        .write_all(code.to_string().as_bytes())
        .expect("Failed to write VERSION_CODE");

    File::create(Path::new(out_dir).join("VERSION_NAME"))
        .expect("Failed to create VERSION_NAME")
        .write_all(name.trim().as_bytes())
        .expect("Failed to write VERSION_NAME");

    let (major, minor, patch) = get_kp_version();
    File::create(Path::new(out_dir).join("kp_version.rs"))
        .expect("Failed to create kp_version.rs")
        .write_all(
            format!(
                "pub const KP_MAJOR: i64 = {major};\n\
                 pub const KP_MINOR: i64 = {minor};\n\
                 pub const KP_PATCH: i64 = {patch};\n"
            )
            .as_bytes(),
        )
        .expect("Failed to write kp_version.rs");
}
