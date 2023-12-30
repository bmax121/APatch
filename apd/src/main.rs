mod assets;
mod cli;
mod defs;
mod event;
mod apd;
mod module;
mod mount;
mod restorecon;
mod utils;

fn main() -> anyhow::Result<()> {
    cli::run()
}
