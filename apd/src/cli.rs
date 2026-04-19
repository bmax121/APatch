use crate::{defs, event, lua, module, module_config, supercall, utils};
#[cfg(target_os = "android")]
use android_logger::Config;
use anyhow::{Context, Result};
use clap::Parser;
#[cfg(target_os = "android")]
use log::LevelFilter;

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

    /// Start uid listener for synchronizing root list
    UidListener,

    /// Resetprop - Magisk-compatible system property tool
    Resetprop(crate::resetprop::Args),

    /// MagiskPolicy - SELinux Policy Patch Tool
    Sepolicy(crate::sepolicy::Args),
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

    /// UudoUninstall module <id>
    UndoUninstall {
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

    /// run action for module <id>
    Action {
        // module id
        id: String,
    },
    /// module lua runner
    Lua {
        // module id
        id: String,
        // lua function
        function: String,
    },
    /// list all modules
    List,

    /// manage module configuration
    Config {
        /// target internal module name (resolved as internal.<name>)
        #[arg(long)]
        internal: Option<String>,
        #[command(subcommand)]
        command: ModuleConfigCmd,
    },
}

#[derive(clap::Subcommand, Debug)]
enum ModuleConfigCmd {
    /// Get a config value
    Get {
        /// config key
        key: String,
    },

    /// Set a config value
    Set {
        /// config key
        key: String,
        /// config value (omit to read from stdin)
        value: Option<String>,
        /// read value from stdin (default if value not provided)
        #[arg(long)]
        stdin: bool,
        /// use temporary config (cleared on reboot)
        #[arg(short, long)]
        temp: bool,
    },

    /// List all config entries
    List,

    /// Delete a config entry
    Delete {
        /// config key
        key: String,
        /// delete from temporary config
        #[arg(short, long)]
        temp: bool,
    },

    /// Clear all config entries
    Clear {
        /// clear temporary config
        #[arg(short, long)]
        temp: bool,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Sepolicy {
    /// Check if sepolicy statement is supported/valid
    Check {
        /// sepolicy statements
        sepolicy: String,
    },
}

pub fn run() -> Result<()> {
    #[cfg(target_os = "android")]
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Trace) // limit log level
            .with_tag("APatchD")
            .with_filter(
                android_logger::FilterBuilder::new()
                    .filter_level(LevelFilter::Trace)
                    .filter_module("notify", LevelFilter::Warn)
                    .build(),
            ),
    );

    #[cfg(not(target_os = "android"))]
    env_logger::init();

    // the kernel executes su with argv[0] = "/system/bin/kp" or "/system/bin/su" or "su" or "kp" and replace it with us
    let arg0 = std::env::args().next().unwrap_or_default();
    if arg0.ends_with("kp") || arg0.ends_with("su") {
        return crate::apd::root_shell();
    }
    if arg0.ends_with("resetprop") {
        let all_args: Vec<String> = std::env::args().collect();
        crate::resetprop::resetprop_main(&all_args)
    }
    if arg0.ends_with("magiskpolicy") {
        let all_args: Vec<String> = std::env::args().collect();
        crate::sepolicy::policy_main(&all_args)
    }

    let cli = Args::parse();

    log::info!("command: {:?}", cli.command);

    if let Some(ref _superkey) = cli.superkey {
        supercall::privilege_apd_profile(&cli.superkey);
    }

    let result = match cli.command {
        Commands::PostFsData => event::on_post_data_fs(cli.superkey),

        Commands::BootCompleted => event::on_boot_completed(cli.superkey),

        Commands::UidListener => event::start_uid_listener(),

        Commands::Module { command } => {
            #[cfg(any(target_os = "linux", target_os = "android"))]
            {
                utils::switch_mnt_ns(1)?;
            }
            match command {
                Module::Install { zip } => module::install_module(&zip),
                Module::Uninstall { id } => module::uninstall_module(&id),
                Module::UndoUninstall { id } => module::undo_uninstall_module(&id),
                Module::Action { id } => module::run_action(&id),
                Module::Lua { id, function } => {
                    lua::run_lua(&id, &function, false, true).map_err(|e| anyhow::anyhow!("{}", e))
                }
                Module::Enable { id } => module::enable_module(&id),
                Module::Disable { id } => module::disable_module(&id),
                Module::List => module::list_modules(),
                Module::Config { internal, command } => {
                    let module_id = match internal {
                        Some(internal_name) => format!("internal.{internal_name}"),
                        None => std::env::var("AP_MODULE").map_err(|_| {
                            anyhow::anyhow!(
                                "This command must be run in the context of a module or passed --internal <name>"
                            )
                        })?,
                    };

                    match command {
                        ModuleConfigCmd::Get { key } => {
                            // Use merge_configs to respect priority (temp overrides persist)
                            let config = module_config::merge_configs(&module_id)?;
                            match config.get(&key) {
                                Some(value) => {
                                    println!("{value}");
                                    Ok(())
                                }
                                None => anyhow::bail!("Key '{key}' not found"),
                            }
                        }
                        ModuleConfigCmd::Set {
                            key,
                            value,
                            stdin,
                            temp,
                        } => {
                            // Validate key at CLI layer for better user experience
                            module_config::validate_config_key(&key)?;

                            // Read value from stdin or argument
                            let value_str = match value {
                                Some(v) if !stdin => v,
                                _ => {
                                    // Read from stdin
                                    use std::io::Read;
                                    let mut buffer = String::new();
                                    std::io::stdin()
                                        .read_to_string(&mut buffer)
                                        .context("Failed to read from stdin")?;
                                    buffer
                                }
                            };

                            // Validate value
                            module_config::validate_config_value(&value_str)?;

                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::set_config_value(
                                &module_id,
                                &key,
                                &value_str,
                                config_type,
                            )
                        }
                        ModuleConfigCmd::List => {
                            let config = module_config::merge_configs(&module_id)?;
                            if config.is_empty() {
                                println!("No config entries found");
                            } else {
                                for (key, value) in config {
                                    println!("{key}={value}");
                                }
                            }
                            Ok(())
                        }
                        ModuleConfigCmd::Delete { key, temp } => {
                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::delete_config_value(&module_id, &key, config_type)
                        }
                        ModuleConfigCmd::Clear { temp } => {
                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::clear_config(&module_id, config_type)
                        }
                    }
                }
            }
        }

        Commands::Services => event::on_services(cli.superkey),

        Commands::Resetprop(resetprop_args) => crate::resetprop::execute(&resetprop_args)
            .inspect_err(|e| {
                if e.downcast_ref::<crate::resetprop::WaitTimeoutError>()
                    .is_some()
                {
                    std::process::exit(2);
                }
            }),

        Commands::Sepolicy(sepolicy_args) => crate::sepolicy::execute(&sepolicy_args),
    };

    if let Err(e) = &result {
        log::error!("Error: {:?}", e);
    }
    result
}
