mod assets;
mod cli;
mod defs;
mod event;
#[cfg(any(target_os = "linux", target_os = "android"))]
mod pty;
mod apd;
mod module;
mod mount;
mod restorecon;
mod utils;
fn main() -> anyhow::Result<()> {
    cli::run()
}
