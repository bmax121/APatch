mod apk_sign;
mod assets;
mod cli;
mod debug;
mod defs;
mod event;
mod ksu;
mod module;
mod mount;
mod restorecon;
mod utils;

fn main() -> anyhow::Result<()> {
    cli::run()
}
