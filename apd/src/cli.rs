use anyhow::Result;
use clap::Parser;

#[cfg(target_os = "android")]
use android_logger::Config;
#[cfg(target_os = "android")]
use log::LevelFilter;

use crate::{defs, event, module, supercall, utils};

/// APatch cli
#[derive(Parser, Debug)]
#[command(author, version = defs::VERSION_CODE, about, long_about = None)]
struct Args {
    #[arg(
        short,
        long,
        value_name = "KEY",
        help = "Super key for authentication root"
    )]
    superkey: Option<String>,
    #[command(subcommand)]
    command: Commands,
}

#[derive(clap::Subcommand, Debug)]
enum Commands {
    /// Manage APatch modules
    Module {
        #[command(subcommand)]
        command: Module,
    },

    /// Trigger `post-fs-data` event
    PostFsData,

    /// Trigger `service` event
    Services,

    /// Trigger `boot-complete` event
    BootCompleted,

    /// Sync package uid from system's packages.list
    SyncPackageUid,
}

#[derive(clap::Subcommand, Debug)]
enum Module {
    /// Install module <ZIP>
    Install {
        /// module zip file path
        zip: String,
    },

    /// Uninstall module <id>
    Uninstall {
        /// module id
        id: String,
    },

    /// enable module <id>
    Enable {
        /// module id
        id: String,
    },

    /// disable module <id>
    Disable {
        // module id
        id: String,
    },

    /// list all modules
    List,
}

pub fn run() -> Result<()> {
    #[cfg(target_os = "android")]
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Trace) // limit log level
            .with_tag("APatchD"),
    );

    #[cfg(not(target_os = "android"))]
    env_logger::init();

    // the kernel executes su with argv[0] = "/system/bin/kp" or "/system/bin/su" or "su" or "kp" and replace it with us
    let arg0 = std::env::args().next().unwrap_or_default();
    if arg0.ends_with("kp") || arg0.ends_with("su") {
        return crate::apd::root_shell();
    }

    let cli = Args::parse();

    log::info!("command: {:?}", cli.command);

    if let Some(ref _superkey) = cli.superkey {
        supercall::privilege_apd_profile(&cli.superkey);
    }

    let result = match cli.command {
        Commands::PostFsData => event::on_post_data_fs(cli.superkey),

        Commands::BootCompleted => event::on_boot_completed(cli.superkey),

        Commands::Module { command } => {
            #[cfg(any(target_os = "linux", target_os = "android"))]
            {
                utils::switch_mnt_ns(1)?;
                utils::unshare_mnt_ns()?;
            }
            match command {
                Module::Install { zip } => module::install_module(&zip),
                Module::Uninstall { id } => module::uninstall_module(&id),
                Module::Enable { id } => module::enable_module(&id),
                Module::Disable { id } => module::disable_module(&id),
                Module::List => module::list_modules(),
            }
        }

        Commands::Services => event::on_services(cli.superkey),

        Commands::SyncPackageUid => event::on_sync_uid(),
    };

    if let Err(e) = &result {
        log::error!("Error: {:?}", e);
    }
    result
}
